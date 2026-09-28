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
import io.trino.filesystem.TrinoFileSystemFactory;
import io.trino.metadata.Metadata;
import io.trino.metadata.QualifiedObjectName;
import io.trino.metadata.TableHandle;
import io.trino.metadata.TableVersion;
import io.trino.metastore.HiveMetastore;
import io.trino.testing.AbstractTestQueryFramework;
import io.trino.testing.QueryRunner;
import io.trino.testing.sql.TestTable;
import org.apache.iceberg.BaseTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.trino.metadata.TableVersion.toTableVersion;
import static io.trino.plugin.iceberg.IcebergTestUtils.getFileSystemFactory;
import static io.trino.plugin.iceberg.IcebergTestUtils.getHiveMetastore;
import static io.trino.spi.StandardErrorCode.NOT_SUPPORTED;
import static io.trino.spi.connector.PointerType.TARGET_ID;
import static io.trino.spi.type.BigintType.BIGINT;
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
            // Trino cannot create branches or tags, so create them through the Iceberg API
            BaseTable icebergTable = loadTable(table.getName());
            long snapshotId = icebergTable.currentSnapshot().snapshotId();
            icebergTable.manageSnapshots()
                    .createBranch("test_branch", snapshotId)
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
            // Trino cannot create branches, so create one through the Iceberg API
            BaseTable icebergTable = loadTable(table.getName());
            icebergTable.manageSnapshots()
                    .createBranch("test_branch", icebergTable.currentSnapshot().snapshotId())
                    .commit();

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
