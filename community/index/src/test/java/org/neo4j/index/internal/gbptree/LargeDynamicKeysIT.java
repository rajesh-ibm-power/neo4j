/*
 * Copyright (c) 2002-2019 "Neo4j,"
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
package org.neo4j.index.internal.gbptree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.neo4j.cursor.RawCursor;
import org.neo4j.helpers.collection.Pair;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.string.UTF8;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.RandomExtension;
import org.neo4j.test.extension.pagecache.PageCacheExtension;
import org.neo4j.test.rule.RandomRule;
import org.neo4j.test.rule.TestDirectory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.index.internal.gbptree.TreeNodeDynamicSize.keyValueSizeCapFromPageSize;
import static org.neo4j.io.pagecache.PageCache.PAGE_SIZE;

@PageCacheExtension
@ExtendWith( RandomExtension.class )
class LargeDynamicKeysIT
{
    @Inject
    private RandomRule random;
    @Inject
    private PageCache pageCache;
    @Inject
    private TestDirectory testDirectory;

    @Test
    void mustStayCorrectWhenInsertingValuesOfIncreasingLength() throws IOException
    {
        mustStayCorrectWhenInsertingValuesOfIncreasingLength( false );
    }

    @Test
    void mustStayCorrectWhenInsertingValuesOfIncreasingLengthInRandomOrder() throws IOException
    {
        mustStayCorrectWhenInsertingValuesOfIncreasingLength( true );
    }

    private void mustStayCorrectWhenInsertingValuesOfIncreasingLength( boolean shuffle ) throws IOException
    {
        Layout<RawBytes,RawBytes> layout = layout();
        try ( GBPTree<RawBytes,RawBytes> index = createIndex( layout ) )
        {
            RawBytes emptyValue = layout.newValue();
            emptyValue.bytes = new byte[0];
            List<Integer> allKeySizes = new ArrayList<>();
            for ( int keySize = 1; keySize < index.keyValueSizeCap(); keySize++ )
            {
                allKeySizes.add( keySize );
            }
            if ( shuffle )
            {
                Collections.shuffle( allKeySizes, random.random() );
            }
            try ( Writer<RawBytes,RawBytes> writer = index.writer() )
            {
                for ( Integer keySize : allKeySizes )
                {
                    RawBytes key = layout.newKey();
                    key.bytes = new byte[keySize];
                    writer.put( key, emptyValue );
                }
            }
            index.consistencyCheck();
            for ( Integer keySize : allKeySizes )
            {
                RawBytes key = layout.newKey();
                key.bytes = new byte[keySize];
                RawCursor<Hit<RawBytes,RawBytes>,IOException> seek = index.seek( key, key );
                assertTrue( seek.next() );
                assertEquals( 0, layout.compare( key, seek.get().key() ) );
                assertFalse( seek.next() );
            }

        }
    }

    @Test
    void shouldWriteAndReadSmallToSemiLargeEntries() throws IOException
    {
        int keyValueSizeCap = keyValueSizeCapFromPageSize( PAGE_SIZE );
        int minValueSize = 0;
        int maxValueSize = random.nextInt( 200 );
        int minKeySize = 4;
        int maxKeySize = keyValueSizeCap / 5;
        shouldWriteAndReadEntriesOfRandomSizes( minKeySize, maxKeySize, minValueSize, maxValueSize );
    }

    @Test
    void shouldWriteAndReadSmallToLargeEntries() throws IOException
    {
        int keyValueSizeCap = keyValueSizeCapFromPageSize( PAGE_SIZE );
        int minValueSize = 0;
        int maxValueSize = random.nextInt( 200 );
        int minKeySize = 4;
        int maxKeySize = keyValueSizeCap - maxValueSize;
        shouldWriteAndReadEntriesOfRandomSizes( minKeySize, maxKeySize, minValueSize, maxValueSize );
    }

    @Test
    void shouldWriteAndReadSemiLargeToLargeEntries() throws IOException
    {
        int keyValueSizeCap = keyValueSizeCapFromPageSize( PAGE_SIZE );
        int minValueSize = 0;
        int maxValueSize = random.nextInt( 200 );
        int minKeySize = keyValueSizeCap / 5;
        int maxKeySize = keyValueSizeCap - maxValueSize;
        shouldWriteAndReadEntriesOfRandomSizes( minKeySize, maxKeySize, minValueSize, maxValueSize );
    }

    private void shouldWriteAndReadEntriesOfRandomSizes( int minKeySize, int maxKeySize, int minValueSize, int maxValueSize ) throws IOException
    {
        // given
        try ( GBPTree<RawBytes,RawBytes> tree = createIndex( layout() ) )
        {
            // when
            Set<String> generatedStrings = new HashSet<>();
            List<Pair<RawBytes,RawBytes>> entries = new ArrayList<>();
            for ( int i = 0; i < 1_000; i++ )
            {
                // value, based on i
                RawBytes value = new RawBytes();
                value.bytes = new byte[random.nextInt( minValueSize, maxValueSize )];
                random.nextBytes( value.bytes );

                // key, randomly generated
                String string;
                do
                {
                    string = random.nextAlphaNumericString( minKeySize, maxKeySize );
                }
                while ( !generatedStrings.add( string ) );
                RawBytes key = new RawBytes();
                key.bytes = UTF8.encode( string );
                entries.add( Pair.of( key, value ) );
            }

            int i = 0;
            try ( Writer<RawBytes,RawBytes> writer = tree.writer() )
            {
                for ( Pair<RawBytes,RawBytes> entry : entries )
                {
                    // write
                    writer.put( entry.first(), entry.other() );
                }
            }

            // then
            for ( Pair<RawBytes,RawBytes> entry : entries )
            {
                try ( RawCursor<Hit<RawBytes,RawBytes>,IOException> seek = tree.seek( entry.first(), entry.first() ) )
                {
                    assertTrue( seek.next() );
                    assertArrayEquals( entry.first().bytes, seek.get().key().bytes );
                    assertArrayEquals( entry.other().bytes, seek.get().value().bytes );
                    assertFalse( seek.next() );
                }
            }
        }
    }

    private SimpleByteArrayLayout layout()
    {
        return new SimpleByteArrayLayout( false );
    }

    private GBPTree<RawBytes,RawBytes> createIndex( Layout<RawBytes,RawBytes> layout ) throws IOException
    {
        // some random padding
        return new GBPTreeBuilder<>( pageCache, testDirectory.file( "index" ), layout ).build();
    }
}
