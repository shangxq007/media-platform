package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.interchange.*;
import org.junit.jupiter.api.Test;
import com.example.platform.timeline.api.serialization.TimelineFrameRateCodec.InvalidCanonicalRateException;
import static org.junit.jupiter.api.Assertions.*;

class TimelineResolverRejectionTest {
    final TimelineSpecResolver resolver=new TimelineSpecResolver(
            new InternalTimelineAdapter(new TimelineExtensionsReader(),new TimelineAssetUriResolver()),new TimelineScriptParser());
    static final String INVALID="{\"schemaVersion\":\"1.0\",\"id\":\"t\",\"project\":{\"frameRate\":{\"num\":30,\"den\":0}},\"composition\":{\"tracks\":[]}}";
    @Test void invalidInternalProjectRateIsNotAnAlternativeFormat(){
        assertThrows(InvalidCanonicalRateException.class,()->resolver.resolve(INVALID));
    }
    @Test void invalidInternalClipRateIsNotAnAlternativeFormat(){
        String input="""
                {"schemaVersion":"1.0","id":"t","project":{"frameRate":{"num":30,"den":1}},
                 "composition":{"tracks":[{"id":"v","type":"VIDEO","clips":[{"id":"c","assetId":"a","uri":"file:///fixture.mp4",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":0}},"duration":{"frame":30,"rate":{"num":30,"den":1}}},
                  "sourceRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]}]}}
                """;
        assertThrows(InvalidCanonicalRateException.class,()->resolver.resolve(input));
    }
    @Test void mixedInternalAndInterchangeFieldsCannotHideInternalFailure() {
        String mixed=INVALID.substring(0,INVALID.length()-1)+",\"tracks\":[{\"children\":[]}],\"outputSpec\":{}}";
        var error=assertThrows(InvalidCanonicalRateException.class,()->resolver.resolve(mixed));
        assertTrue(error.getMessage().toLowerCase().contains("den"),error.getMessage());
    }
    @Test void missingOrNullOptionalRateKeepsPolicyButMalformedSuppliedValuesReject() {
        var accepted=resolver.resolve("{\"schemaVersion\":\"1.0\",\"composition\":{\"tracks\":[]}}").orElseThrow();
        assertEquals(com.example.platform.shared.time.FrameRate.of(30,1),accepted.outputSpec().frameRate());
        var explicitNull=resolver.resolve("{\"schemaVersion\":\"1.0\",\"project\":{\"frameRate\":null},\"composition\":{\"tracks\":[]}}").orElseThrow();
        assertEquals(com.example.platform.shared.time.FrameRate.of(30,1),explicitNull.outputSpec().frameRate());
        for(String value:java.util.List.of("{\"num\":0,\"den\":1}","{\"num\":30,\"den\":-1}","\"30\"")) {
            String input="{\"schemaVersion\":\"1.0\",\"project\":{\"frameRate\":"+value+"},\"composition\":{\"tracks\":[]}}";
            assertThrows(InvalidCanonicalRateException.class,()->resolver.resolve(input),value);
        }
    }
    @Test void supportedInternalAndOtioInputsStillResolve() throws Exception {
        String internal=java.nio.file.Files.readString(com.example.platform.shared.test.FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json"));
        assertFalse(resolver.resolve(internal).orElseThrow().tracks().isEmpty());
        String otio="""
                {"tracks":[{"type":"VIDEO","children":[{"media_reference":"file:///fixture.mp4",
                 "source_range":{"start_time":0,"duration":1}}]}]}
                """;
        assertFalse(resolver.resolve(otio).orElseThrow().tracks().isEmpty());
        assertFalse(resolver.isInternalTimelineJson(otio));
    }
    @Test void unknownFormatsAreNonMatchesAndMalformedJsonCannotSelectAPlan() {
        assertTrue(resolver.resolve("plain non-Timeline input").isEmpty());
        assertTrue(resolver.resolve("{\"metadata\":{\"tracks\":[]}}").isEmpty());
        assertTrue(resolver.resolve("{\"description\":\"tracks\"}").isEmpty());
        assertTrue(resolver.resolve("[]").isEmpty());
        assertThrows(IllegalArgumentException.class,()->resolver.resolve("{\"tracks\":"));
    }
    @Test void actualExecutionDispatchHelperReceivesRejectionBeforeProviderOrDag() {
        var engine=org.mockito.Mockito.mock(com.example.platform.render.infrastructure.providerruntime.engine.ProviderRuntimeEngine.class);
        var inspector=org.mockito.Mockito.mock(com.example.platform.render.app.EffectTimelineInspector.class);
        var execution=new com.example.platform.render.app.RenderJobExecutionService(null,null,null,engine,
                new TimelineScriptParser(),resolver,null,null,null,null,inspector,null,null,null,null,null,
                new TimelineExtensionsReader(),null,null,null,null, org.mockito.Mockito.mock(com.example.platform.render.api.context.ExecutionContextQueries.class));
        assertThrows(InvalidCanonicalRateException.class,()->org.springframework.test.util.ReflectionTestUtils.invokeMethod(execution,
                "executeRenderWithOptionalDag","job","project",INVALID,"profile","tenant",null));
        org.mockito.Mockito.verifyNoInteractions(engine,inspector);
    }
}
