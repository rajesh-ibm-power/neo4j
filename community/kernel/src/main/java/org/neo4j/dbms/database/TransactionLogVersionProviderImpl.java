/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.dbms.database;

import org.neo4j.kernel.impl.transaction.log.entry.LogEntryParserSetVersion;

public class TransactionLogVersionProviderImpl implements TransactionLogVersionProvider
{
    private final DbmsRuntimeRepository dbmsRuntimeRepository;
    private final boolean useSeparateCheckpointFiles;

    public TransactionLogVersionProviderImpl( DbmsRuntimeRepository dbmsRuntimeRepository, boolean useSeparateCheckpointFiles )
    {
        this.dbmsRuntimeRepository = dbmsRuntimeRepository;
        this.useSeparateCheckpointFiles = useSeparateCheckpointFiles;
    }

    @Override
    public byte getVersion()
    {
        DbmsRuntimeVersion dbmsRuntimeVersion = dbmsRuntimeRepository.getVersion();

        LogEntryParserSetVersion parserSet;
        if ( dbmsRuntimeVersion == DbmsRuntimeVersion.V4_2 && useSeparateCheckpointFiles )
        {
            parserSet = LogEntryParserSetVersion.LogEntryV4_2;
        }
        else
        {
            parserSet = LogEntryParserSetVersion.LogEntryV4_0;
        }

        return parserSet.getVersionByte();
    }
}
