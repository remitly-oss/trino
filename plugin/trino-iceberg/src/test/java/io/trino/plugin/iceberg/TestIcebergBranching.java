/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.iceberg;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.filesystem.TrinoFileSystemFactory;
import io.trino.metadata.Metadata;
import io.trino.metadata.QualifiedObjectName;
import io.trino.metadata.TableHandle;
import io.trino.metadata.TableVersion;
import io.trino.metastore.HiveMetastore;
import io.trino.plugin.iceberg.catalog.TrinoCatalog;
import io.trino.spi.connector.SchemaTableName;
import io.trino.testing.AbstractTestQueryFramework;
import io.trino.testing.QueryRunner;
import io.trino.testing.sql.TestTable;
import org.apache.iceberg.BaseTable;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SortOrder;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.trino.metadata.TableVersion.toTableVersion;
import static io.trino.plugin.iceberg.IcebergTestUtils.SESSION;
import static io.trino.plugin.iceberg.IcebergTestUtils.getFileSystemFactory;
import static io.trino.plugin.iceberg.IcebergTestUtils.getHiveMetastore;
import static io.trino.plugin.iceberg.IcebergTestUtils.getTrinoCatalog;
import static io.trino.spi.StandardErrorCode.BRANCH_ALREADY_EXISTS;
import static io.trino.spi.StandardErrorCode.BRANCH_NOT_FOUND;
import static io.trino.spi.StandardErrorCode.INVALID_ARGUMENTS;
import static io.trino.spi.StandardErrorCode.NOT_SUPPORTED;
import static io.trino.spi.connector.PointerType.TARGET_ID;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.testing.TestingNames.randomNameSuffix;
import static io.trino.testing.assertions.TrinoExceptionAssert.assertTrinoExceptionThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

final class TestIcebergBranching
        extends AbstractTestQueryFramework
{
    private HiveMetastore metastore;
    private TrinoFileSystemFactory fileSystemFactory;

    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        return IcebergQueryRunner.builder().build();
    }

    @BeforeAll
    void initCatalogAccess()
    {
        metastore = getHiveMetastore(getQueryRunner());
        fileSystemFactory = getFileSystemFactory(getQueryRunner());
    }

    @Test
    void testTableHandleBranch()
    {
        try (TestTable table = newTrinoTable("test_table_handle_branch_", "(id integer)", ImmutableList.of("1"))) {
            assertUpdate("CREATE BRANCH test_branch IN TABLE " + table.getName());
            // Trino cannot create tags, so create one through the Iceberg API
            BaseTable icebergTable = loadTable(table.getName());
            long snapshotId = icebergTable.currentSnapshot().snapshotId();
            icebergTable.manageSnapshots()
                    .createTag("test_tag", snapshotId)
                    .commit();

            assertThat(tableHandle(table.getName(), Optional.of(toTableVersion("test_branch"))).getBranch()).contains("test_branch");
            assertThat(tableHandle(table.getName(), Optional.of(toTableVersion("main"))).getBranch()).contains("main");
            assertThat(tableHandle(table.getName(), Optional.of(toTableVersion("test_tag"))).getBranch()).isEmpty();
            assertThat(tableHandle(table.getName(), Optional.of(new TableVersion(TARGET_ID, BIGINT, snapshotId))).getBranch()).isEmpty();
            assertThat(tableHandle(table.getName(), Optional.empty()).getBranch()).isEmpty();

            assertThat(query("SELECT id FROM " + table.getName() + " FOR VERSION AS OF 'test_branch'"))
                    .matches("VALUES 1");
        }
    }

    @Test
    void testWriteLayoutsRejectBranch()
    {
        try (TestTable table = newTrinoTable("test_write_layouts_reject_branch_", "(id integer)", ImmutableList.of("1"))) {
            assertUpdate("CREATE BRANCH test_branch IN TABLE " + table.getName());

            Metadata metadata = getQueryRunner().getPlannerContext().getMetadata();
            QualifiedObjectName tableName = qualifiedName(table.getName());
            newTransaction().singleStatement().execute(getSession(), session -> {
                TableHandle branchHandle = metadata.getTableHandle(session, tableName, Optional.empty(), Optional.of(toTableVersion("test_branch"))).orElseThrow();
                assertTrinoExceptionThrownBy(() -> metadata.getInsertLayout(session, branchHandle))
                        .hasErrorCode(NOT_SUPPORTED)
                        .hasMessage("Writing to Iceberg branches is not supported");
                assertTrinoExceptionThrownBy(() -> metadata.getUpdateLayout(session, branchHandle))
                        .hasErrorCode(NOT_SUPPORTED)
                        .hasMessage("Writing to Iceberg branches is not supported");

                TableHandle currentHandle = metadata.getTableHandle(session, tableName).orElseThrow();
                assertThat(metadata.getInsertLayout(session, currentHandle)).isEmpty();
                assertThat(metadata.getUpdateLayout(session, currentHandle)).isEmpty();
            });
        }
    }

    @Test
    void testShowBranches()
    {
        try (TestTable table = newTrinoTable("test_show_branches_", "(id integer)", ImmutableList.of("1"))) {
            assertThat(query("SHOW BRANCHES IN TABLE " + table.getName()))
                    .skippingTypesCheck()
                    .result()
                    .hasColumnNames("Branch")
                    .matches("VALUES 'main'");

            assertUpdate("CREATE BRANCH branch_a IN TABLE " + table.getName());
            assertUpdate("INSERT INTO " + table.getName() + " VALUES 2", 1);
            assertUpdate("CREATE BRANCH branch_b IN TABLE " + table.getName());
            // Trino cannot create tags, so create one through the Iceberg API
            BaseTable icebergTable = loadTable(table.getName());
            icebergTable.manageSnapshots()
                    .createTag("test_tag", icebergTable.currentSnapshot().snapshotId())
                    .commit();

            assertThat(query("SHOW BRANCHES FROM TABLE " + table.getName()))
                    .skippingTypesCheck()
                    .matches("VALUES 'main', 'branch_a', 'branch_b'");
        }
    }

    @Test
    void testShowBranchesOnTableWithoutSnapshot()
    {
        String tableName = "test_show_branches_without_snapshot_" + randomNameSuffix();
        createTableWithoutSnapshot(tableName);
        try {
            // Iceberg creates the main branch with the first snapshot
            assertThat(query("SHOW BRANCHES IN TABLE " + tableName))
                    .result()
                    .hasColumnNames("Branch")
                    .isEmpty();
        }
        finally {
            assertUpdate("DROP TABLE " + tableName);
        }
    }

    @Test
    void testCreateBranch()
    {
        try (TestTable table = newTrinoTable("test_create_branch_", "(id integer)", ImmutableList.of("1"))) {
            assertUpdate("CREATE BRANCH test_branch IN TABLE " + table.getName());
            assertUpdate("INSERT INTO " + table.getName() + " VALUES 2", 1);

            assertThat(query("SHOW BRANCHES IN TABLE " + table.getName()))
                    .skippingTypesCheck()
                    .matches("VALUES 'main', 'test_branch'");
            assertThat(query("SELECT id FROM " + table.getName() + " FOR VERSION AS OF 'test_branch'"))
                    .matches("VALUES 1");
            assertThat(query("SELECT id FROM " + table.getName()))
                    .matches("VALUES 1, 2");
        }
    }

    @Test
    void testCreateBranchFromBranch()
    {
        try (TestTable table = newTrinoTable("test_create_branch_from_", "(id integer)", ImmutableList.of("1"))) {
            assertUpdate("CREATE BRANCH branch_a IN TABLE " + table.getName());
            assertUpdate("INSERT INTO " + table.getName() + " VALUES 2", 1);
            assertUpdate("CREATE BRANCH branch_b IN TABLE " + table.getName() + " FROM branch_a");
            assertUpdate("CREATE BRANCH branch_c IN TABLE " + table.getName() + " FROM main");

            assertThat(query("SELECT id FROM " + table.getName() + " FOR VERSION AS OF 'branch_b'"))
                    .matches("VALUES 1");
            assertThat(query("SELECT id FROM " + table.getName() + " FOR VERSION AS OF 'branch_c'"))
                    .matches("VALUES 1, 2");

            assertThat(query("CREATE BRANCH branch_d IN TABLE " + table.getName() + " FROM missing_branch"))
                    .failure()
                    .hasErrorCode(BRANCH_NOT_FOUND)
                    .hasMessage("line 1:1: Branch 'missing_branch' does not exist");
        }
    }

    @Test
    void testCreateExistingBranch()
    {
        try (TestTable table = newTrinoTable("test_create_existing_branch_", "(id integer)", ImmutableList.of("1"))) {
            assertUpdate("CREATE BRANCH test_branch IN TABLE " + table.getName());
            assertUpdate("INSERT INTO " + table.getName() + " VALUES 2", 1);

            assertThat(query("CREATE BRANCH test_branch IN TABLE " + table.getName()))
                    .failure()
                    .hasErrorCode(BRANCH_ALREADY_EXISTS)
                    .hasMessage("line 1:1: Branch 'test_branch' already exists");
            assertUpdate("CREATE BRANCH IF NOT EXISTS test_branch IN TABLE " + table.getName());

            assertThat(query("SELECT id FROM " + table.getName() + " FOR VERSION AS OF 'test_branch'"))
                    .matches("VALUES 1");
        }
    }

    @Test
    void testCreateOrReplaceBranch()
    {
        try (TestTable table = newTrinoTable("test_create_or_replace_branch_", "(id integer)", ImmutableList.of("1"))) {
            assertUpdate("CREATE BRANCH branch_a IN TABLE " + table.getName());
            assertUpdate("CREATE BRANCH test_branch IN TABLE " + table.getName());
            // Trino cannot set branch retention, so set it through the Iceberg API
            loadTable(table.getName()).manageSnapshots()
                    .setMinSnapshotsToKeep("test_branch", 7)
                    .commit();
            assertUpdate("INSERT INTO " + table.getName() + " VALUES 2", 1);

            assertUpdate("CREATE OR REPLACE BRANCH test_branch IN TABLE " + table.getName());
            assertThat(query("SELECT id FROM " + table.getName() + " FOR VERSION AS OF 'test_branch'"))
                    .matches("VALUES 1, 2");
            assertThat(query("SELECT min_snapshots_to_keep FROM \"" + table.getName() + "$refs\" WHERE name = 'test_branch'"))
                    .matches("VALUES 7");

            assertUpdate("CREATE OR REPLACE BRANCH test_branch IN TABLE " + table.getName() + " FROM branch_a");
            assertThat(query("SELECT id FROM " + table.getName() + " FOR VERSION AS OF 'test_branch'"))
                    .matches("VALUES 1");

            assertUpdate("CREATE OR REPLACE BRANCH new_branch IN TABLE " + table.getName());
            assertThat(query("SELECT id FROM " + table.getName() + " FOR VERSION AS OF 'new_branch'"))
                    .matches("VALUES 1, 2");
        }
    }

    @Test
    void testCreateOrReplaceMainBranch()
    {
        try (TestTable table = newTrinoTable("test_create_or_replace_main_", "(id integer)", ImmutableList.of("1"))) {
            assertUpdate("CREATE BRANCH test_branch IN TABLE " + table.getName());
            assertUpdate("INSERT INTO " + table.getName() + " VALUES 2", 1);

            assertThat(query("CREATE OR REPLACE BRANCH main IN TABLE " + table.getName() + " FROM test_branch"))
                    .failure()
                    .hasErrorCode(NOT_SUPPORTED)
                    .hasMessage("Creating or replacing the main branch is not supported");
            assertThat(query("SELECT id FROM " + table.getName()))
                    .matches("VALUES 1, 2");
        }
    }

    @Test
    void testCreateBranchWithProperties()
    {
        try (TestTable table = newTrinoTable("test_create_branch_properties_", "(id integer)", ImmutableList.of("1"))) {
            assertThat(query("CREATE BRANCH test_branch WITH (min_snapshots_to_keep = 7) IN TABLE " + table.getName()))
                    .failure()
                    .hasMessageContaining("branch property 'min_snapshots_to_keep' does not exist");
        }
    }

    @Test
    void testCreateBranchOnTableWithoutSnapshot()
    {
        String tableName = "test_create_branch_without_snapshot_" + randomNameSuffix();
        createTableWithoutSnapshot(tableName);
        try {
            assertUpdate("CREATE BRANCH test_branch IN TABLE " + tableName);
            assertThat(query("SHOW BRANCHES IN TABLE " + tableName))
                    .skippingTypesCheck()
                    .matches("VALUES 'test_branch'");
            assertThat(query("SELECT * FROM " + tableName + " FOR VERSION AS OF 'test_branch'"))
                    .returnsEmptyResult();

            assertThat(query("CREATE BRANCH main IN TABLE " + tableName))
                    .failure()
                    .hasErrorCode(NOT_SUPPORTED)
                    .hasMessage("Creating or replacing the main branch is not supported");
            assertThat(query("CREATE BRANCH other_branch IN TABLE " + tableName + " FROM main"))
                    .failure()
                    .hasErrorCode(BRANCH_NOT_FOUND)
                    .hasMessage("line 1:1: Branch 'main' does not exist");
            assertThat(query("CREATE OR REPLACE BRANCH test_branch IN TABLE " + tableName))
                    .failure()
                    .hasErrorCode(NOT_SUPPORTED)
                    .hasMessage("Replacing a branch in a table without snapshots is not supported");
        }
        finally {
            assertUpdate("DROP TABLE " + tableName);
        }
    }

    @Test
    void testDropBranch()
    {
        try (TestTable table = newTrinoTable("test_drop_branch_", "(id integer)", ImmutableList.of("1"))) {
            assertUpdate("CREATE BRANCH test_branch IN TABLE " + table.getName());
            assertUpdate("CREATE BRANCH other_branch IN TABLE " + table.getName());

            assertUpdate("DROP BRANCH test_branch IN TABLE " + table.getName());
            assertThat(query("SHOW BRANCHES IN TABLE " + table.getName()))
                    .skippingTypesCheck()
                    .matches("VALUES 'main', 'other_branch'");

            assertThat(query("DROP BRANCH test_branch IN TABLE " + table.getName()))
                    .failure()
                    .hasErrorCode(BRANCH_NOT_FOUND)
                    .hasMessage("line 1:1: Branch 'test_branch' does not exist");
            assertUpdate("DROP BRANCH IF EXISTS test_branch IN TABLE " + table.getName());

            assertThat(query("SELECT id FROM " + table.getName()))
                    .matches("VALUES 1");
        }
    }

    @Test
    void testDropMainBranch()
    {
        try (TestTable table = newTrinoTable("test_drop_main_branch_", "(id integer)", ImmutableList.of("1"))) {
            assertThat(query("DROP BRANCH main IN TABLE " + table.getName()))
                    .failure()
                    .hasErrorCode(INVALID_ARGUMENTS)
                    .hasMessage("Cannot drop the main branch");
            assertThat(query("SHOW BRANCHES IN TABLE " + table.getName()))
                    .skippingTypesCheck()
                    .matches("VALUES 'main'");
        }
    }

    @Test
    void testCreateAndDropBranchIsCaseSensitive()
    {
        try (TestTable table = newTrinoTable("test_create_drop_branch_case_", "(id integer)", ImmutableList.of("1"))) {
            assertUpdate("CREATE BRANCH test_branch IN TABLE " + table.getName());
            assertUpdate("CREATE BRANCH TEST_BRANCH IN TABLE " + table.getName());
            assertThat(query("SHOW BRANCHES IN TABLE " + table.getName()))
                    .skippingTypesCheck()
                    .matches("VALUES 'main', 'test_branch', 'TEST_BRANCH'");

            assertUpdate("DROP BRANCH TEST_BRANCH IN TABLE " + table.getName());
            assertThat(query("SHOW BRANCHES IN TABLE " + table.getName()))
                    .skippingTypesCheck()
                    .matches("VALUES 'main', 'test_branch'");
        }
    }

    @Test
    void testBranchOperationsOnTag()
    {
        try (TestTable table = newTrinoTable("test_branch_operations_on_tag_", "(id integer)", ImmutableList.of("1"))) {
            // Trino cannot create tags, so create one through the Iceberg API
            BaseTable icebergTable = loadTable(table.getName());
            icebergTable.manageSnapshots()
                    .createTag("test_tag", icebergTable.currentSnapshot().snapshotId())
                    .commit();

            assertThat(query("CREATE BRANCH test_tag IN TABLE " + table.getName()))
                    .failure()
                    .hasErrorCode(INVALID_ARGUMENTS)
                    .hasMessage("Cannot create branch 'test_tag': a tag with that name already exists");
            assertThat(query("CREATE OR REPLACE BRANCH test_tag IN TABLE " + table.getName()))
                    .failure()
                    .hasErrorCode(INVALID_ARGUMENTS)
                    .hasMessage("Cannot create branch 'test_tag': a tag with that name already exists");
            assertThat(query("CREATE BRANCH test_branch IN TABLE " + table.getName() + " FROM test_tag"))
                    .failure()
                    .hasErrorCode(BRANCH_NOT_FOUND)
                    .hasMessage("line 1:1: Branch 'test_tag' does not exist");
            assertThat(query("DROP BRANCH test_tag IN TABLE " + table.getName()))
                    .failure()
                    .hasErrorCode(BRANCH_NOT_FOUND)
                    .hasMessage("line 1:1: Branch 'test_tag' does not exist");

            assertThat(query("SELECT name, type FROM \"" + table.getName() + "$refs\""))
                    .skippingTypesCheck()
                    .matches("VALUES ('main', 'BRANCH'), ('test_tag', 'TAG')");
        }
    }

    @Test
    void testWriteToBranch()
    {
        try (TestTable table = newTrinoTable("test_write_to_branch_", "(id integer)", ImmutableList.of("1"))) {
            assertUpdate("CREATE BRANCH test_branch IN TABLE " + table.getName());
            assertUpdate("INSERT INTO " + table.getName() + " VALUES 2", 1);

            for (String branch : ImmutableList.of("test_branch", "main")) {
                String target = table.getName() + "@" + branch;
                assertThat(query("INSERT INTO " + target + " VALUES 3"))
                        .failure()
                        .hasErrorCode(NOT_SUPPORTED)
                        .hasMessage("Writing to Iceberg branches is not supported");
                assertThat(query("UPDATE " + target + " SET id = 3"))
                        .failure()
                        .hasErrorCode(NOT_SUPPORTED)
                        .hasMessage("Writing to Iceberg branches is not supported");
                assertThat(query("DELETE FROM " + target))
                        .failure()
                        .hasErrorCode(NOT_SUPPORTED)
                        .hasMessage("Writing to Iceberg branches is not supported");
                assertThat(query("MERGE INTO " + target + " t USING (VALUES 1) s(id) ON t.id = s.id WHEN MATCHED THEN DELETE"))
                        .failure()
                        .hasErrorCode(NOT_SUPPORTED)
                        .hasMessage("Writing to Iceberg branches is not supported");
            }

            assertThat(query("SELECT id FROM " + table.getName()))
                    .matches("VALUES 1, 2");
            assertThat(query("SELECT id FROM " + table.getName() + " FOR VERSION AS OF 'test_branch'"))
                    .matches("VALUES 1");
        }
    }

    @Test
    void testWriteToMissingBranch()
    {
        try (TestTable table = newTrinoTable("test_write_to_missing_branch_", "(id integer)", ImmutableList.of("1"))) {
            BaseTable icebergTable = loadTable(table.getName());
            icebergTable.manageSnapshots()
                    .createTag("test_tag", icebergTable.currentSnapshot().snapshotId())
                    .commit();

            assertThat(query("INSERT INTO " + table.getName() + "@missing_branch VALUES 2"))
                    .failure()
                    .hasErrorCode(BRANCH_NOT_FOUND)
                    .hasMessage("line 1:1: Branch 'missing_branch' does not exist");
            assertThat(query("INSERT INTO " + table.getName() + "@test_tag VALUES 2"))
                    .failure()
                    .hasErrorCode(BRANCH_NOT_FOUND)
                    .hasMessage("line 1:1: Branch 'test_tag' does not exist");
        }
    }

    private void createTableWithoutSnapshot(String tableName)
    {
        // Trino CREATE TABLE adds an empty snapshot, so use the Iceberg API instead
        TrinoCatalog catalog = getTrinoCatalog(metastore, fileSystemFactory, "iceberg");
        SchemaTableName name = new SchemaTableName("tpch", tableName);
        catalog.newCreateTableTransaction(
                        SESSION,
                        name,
                        new Schema(Types.NestedField.optional(1, "id", Types.IntegerType.get())),
                        PartitionSpec.unpartitioned(),
                        SortOrder.unsorted(),
                        Optional.ofNullable(catalog.defaultTableLocation(SESSION, name)),
                        ImmutableMap.of())
                .commitTransaction();
        assertThat(loadTable(tableName).currentSnapshot()).isNull();
    }

    private IcebergTableHandle tableHandle(String tableName, Optional<TableVersion> endVersion)
    {
        Metadata metadata = getQueryRunner().getPlannerContext().getMetadata();
        return newTransaction().execute(getSession(), session -> {
            TableHandle handle = metadata.getTableHandle(session, qualifiedName(tableName), Optional.empty(), endVersion).orElseThrow();
            return (IcebergTableHandle) handle.connectorHandle();
        });
    }

    private QualifiedObjectName qualifiedName(String tableName)
    {
        return new QualifiedObjectName(getSession().getCatalog().orElseThrow(), getSession().getSchema().orElseThrow(), tableName);
    }

    private BaseTable loadTable(String tableName)
    {
        return IcebergTestUtils.loadTable(tableName, metastore, fileSystemFactory, "iceberg", "tpch");
    }
}
