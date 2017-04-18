/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.aggregations;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ContextParser;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.action.search.RestSearchAction;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.aggregations.metrics.cardinality.CardinalityAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.cardinality.ParsedCardinality;
import org.elasticsearch.search.aggregations.metrics.percentiles.hdr.InternalHDRPercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.hdr.ParsedHDRPercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.InternalTDigestPercentileRanks;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentileRanks;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.test.AbstractWireSerializingTestCase;
import org.elasticsearch.test.ESTestCase;
import org.hamcrest.CoreMatchers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.*;
import static org.elasticsearch.common.xcontent.XContentHelper.toXContent;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertToXContentEquivalent;
import static org.hamcrest.CoreMatchers.containsString;

public abstract class InternalAggregationTestCase<T extends InternalAggregation> extends ESTestCase {

    private final NamedWriteableRegistry namedWriteableRegistry = new NamedWriteableRegistry(
            new SearchModule(Settings.EMPTY, false, emptyList()).getNamedWriteables());

    private final NamedXContentRegistry namedXContentRegistry = new NamedXContentRegistry(getNamedXContents());

    static List<NamedXContentRegistry.Entry> getNamedXContents() {
        Map<String, ContextParser<Object, ? extends Aggregation>> namedXContents = new HashMap<>();
        namedXContents.put(CardinalityAggregationBuilder.NAME, (p, c) -> ParsedCardinality.fromXContent(p, (String) c));
        namedXContents.put(InternalHDRPercentileRanks.NAME, (p, c) -> ParsedHDRPercentileRanks.fromXContent(p, (String) c));
        namedXContents.put(InternalTDigestPercentileRanks.NAME, (p, c) -> ParsedTDigestPercentileRanks.fromXContent(p, (String) c));

        return namedXContents.entrySet().stream()
                .map(entry -> new NamedXContentRegistry.Entry(Aggregation.class, new ParseField(entry.getKey()), entry.getValue()))
                .collect(Collectors.toList());
    }

    protected abstract T createTestInstance(String name, List<PipelineAggregator> pipelineAggregators, Map<String, Object> metaData);

    protected final T createTestInstance() {
        String name = randomAlphaOfLength(5);
        List<PipelineAggregator> pipelineAggregators = new ArrayList<>();
        // TODO populate pipelineAggregators
        Map<String, Object> metaData = null;
        if (randomBoolean()) {
            metaData = new HashMap<>();
            int metaDataCount = between(0, 10);
            while (metaData.size() < metaDataCount) {
                metaData.put(randomAlphaOfLength(5), randomAlphaOfLength(5));
            }
        }
        return createTestInstance(name, pipelineAggregators, metaData);
    }

    @Override
    protected NamedXContentRegistry xContentRegistry() {
        return namedXContentRegistry;
    }

    public final void testFromXContent() throws IOException {
        final NamedXContentRegistry xContentRegistry = xContentRegistry();
        final T aggregation = createTestInstance();

        final ToXContent.Params params = new ToXContent.MapParams(singletonMap(RestSearchAction.TYPED_KEYS_PARAM, "true"));
        final boolean humanReadable = randomBoolean();
        final XContentType xContentType = randomFrom(XContentType.values());
        final BytesReference originalBytes = toShuffledXContent(aggregation, xContentType, params, humanReadable);

        Aggregation parsedAggregation;
        try (XContentParser parser = xContentType.xContent().createParser(xContentRegistry, originalBytes)) {
            assertEquals(XContentParser.Token.START_OBJECT, parser.nextToken());
            assertEquals(XContentParser.Token.FIELD_NAME, parser.nextToken());

            String currentName = parser.currentName();
            int i = currentName.indexOf(InternalAggregation.TYPED_KEYS_DELIMITER);
            String aggType = currentName.substring(0, i);
            String aggName = currentName.substring(i + 1);

            parsedAggregation = parser.namedObject(Aggregation.class, aggType, aggName);

            assertEquals(XContentParser.Token.END_OBJECT, parser.currentToken());
            assertEquals(XContentParser.Token.END_OBJECT, parser.nextToken());
            assertNull(parser.nextToken());

            assertEquals(aggregation.getName(), parsedAggregation.getName());
            assertEquals(aggregation.getMetaData(), parsedAggregation.getMetaData());

            assertTrue(parsedAggregation instanceof ParsedAggregation);
            assertEquals(aggregation.getType(), ((ParsedAggregation) parsedAggregation).getType());

            final BytesReference parsedBytes = toXContent((ToXContent) parsedAggregation, xContentType, params, humanReadable);
            assertToXContentEquivalent(originalBytes, parsedBytes, xContentType);
            assertFromXContent(aggregation, (ParsedAggregation) parsedAggregation);

        } catch (NamedXContentRegistry.UnknownNamedObjectException e) {
            //norelease Remove this catch block when all aggregations can be parsed back.
            assertThat(e.getMessage(), containsString("Unknown Aggregation"));
        }
    }

    //norelease TODO make abstract
    protected void assertFromXContent(T aggregation, ParsedAggregation parsedAggregation) {
    }
}
