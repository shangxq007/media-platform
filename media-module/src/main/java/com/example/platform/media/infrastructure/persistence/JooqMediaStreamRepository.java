package com.example.platform.media.infrastructure.persistence;

import static com.example.platform.typedschema.jooq.generated.tables.MediaStream.MEDIA_STREAM;

import com.example.platform.media.app.MediaStreamRepository;
import com.example.platform.media.domain.description.SourceAudioDescription;
import com.example.platform.media.domain.description.SourceColorDescription;
import com.example.platform.media.domain.description.SourceVideoDescription;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStream;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.media.domain.stream.StreamKind;
import com.example.platform.media.domain.time.TimeBase;
import com.example.platform.shared.time.FrameRate;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * jOOQ implementation over the canonical media_stream table. All time/rate
 * values are exact rationals (timebase_num/den, rate_num/den). No double
 * time/rate authority exists in canonical persistence.
 */
@Repository
public class JooqMediaStreamRepository implements MediaStreamRepository {

    private final DSLContext dsl;

    public JooqMediaStreamRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void saveAll(MediaAssetId mediaAssetId, List<MediaStream> streams) {
        for (MediaStream s : streams) {
            dsl.insertInto(MEDIA_STREAM)
                    .columns(MEDIA_STREAM.ID, MEDIA_STREAM.MEDIA_ASSET_ID, MEDIA_STREAM.STREAM_INDEX,
                            MEDIA_STREAM.STREAM_KIND, MEDIA_STREAM.CODEC,
                            MEDIA_STREAM.TIMEBASE_NUM, MEDIA_STREAM.TIMEBASE_DEN,
                            MEDIA_STREAM.RATE_NUM, MEDIA_STREAM.RATE_DEN, MEDIA_STREAM.IS_VFR,
                            MEDIA_STREAM.WIDTH, MEDIA_STREAM.HEIGHT, MEDIA_STREAM.PIXEL_FORMAT,
                            MEDIA_STREAM.SAMPLE_RATE, MEDIA_STREAM.CHANNELS, MEDIA_STREAM.CHANNEL_LAYOUT,
                            MEDIA_STREAM.SAMPLE_FORMAT, MEDIA_STREAM.BIT_DEPTH,
                            MEDIA_STREAM.COLOR_PRIMARIES, MEDIA_STREAM.COLOR_TRANSFER,
                            MEDIA_STREAM.COLOR_MATRIX, MEDIA_STREAM.COLOR_RANGE,
                            MEDIA_STREAM.HDR_MASTERING_DISPLAY_REF, MEDIA_STREAM.HDR_CONTENT_LIGHT_REF,
                            MEDIA_STREAM.CONTAINER_STREAM_DESCRIPTION)
                    .values(s.id().value(), mediaAssetId.value(), s.streamIndex(),
                            s.kind().name(), s.codec(),
                            s.timeBase().numerator(), s.timeBase().denominator(),
                            s.nominalFrameRate() != null ? s.nominalFrameRate().numerator().longValueExact() : null,
                            s.nominalFrameRate() != null ? s.nominalFrameRate().denominator() : null,
                            s.isVfr(),
                            s.video() != null ? s.video().width() : null,
                            s.video() != null ? s.video().height() : null,
                            s.video() != null ? s.video().pixelFormat() : null,
                            s.audio() != null ? s.audio().sampleRate() : null,
                            s.audio() != null ? s.audio().channels() : null,
                            s.audio() != null ? s.audio().channelLayout() : null,
                            s.audio() != null ? s.audio().sampleFormat() : null,
                            s.audio() != null ? s.audio().bitDepth() : null,
                            s.color() != null ? s.color().primaries() : null,
                            s.color() != null ? s.color().transfer() : null,
                            s.color() != null ? s.color().matrix() : null,
                            s.color() != null ? s.color().range() : null,
                            s.color() != null ? s.color().hdrMasteringDisplayReference() : null,
                            s.color() != null ? s.color().hdrContentLightReference() : null,
                            s.containerStreamDescription())
                    .execute();
        }
    }

    @Override
    public List<MediaStream> findByMediaAssetId(MediaAssetId mediaAssetId) {
        return dsl.selectFrom(MEDIA_STREAM)
                .where(MEDIA_STREAM.MEDIA_ASSET_ID.eq(mediaAssetId.value()))
                .orderBy(MEDIA_STREAM.STREAM_INDEX)
                .fetch()
                .map(r -> new MediaStream(
                        MediaStreamId.of(r.get(MEDIA_STREAM.ID)),
                        r.get(MEDIA_STREAM.STREAM_INDEX),
                        StreamKind.valueOf(r.get(MEDIA_STREAM.STREAM_KIND)),
                        r.get(MEDIA_STREAM.CODEC),
                        TimeBase.of(r.get(MEDIA_STREAM.TIMEBASE_NUM), r.get(MEDIA_STREAM.TIMEBASE_DEN)),
                        r.get(MEDIA_STREAM.RATE_NUM) != null && r.get(MEDIA_STREAM.RATE_DEN) != null
                                ? FrameRate.of(r.get(MEDIA_STREAM.RATE_NUM), r.get(MEDIA_STREAM.RATE_DEN)) : null,
                        Boolean.TRUE.equals(r.get(MEDIA_STREAM.IS_VFR)),
                        r.get(MEDIA_STREAM.WIDTH) != null || r.get(MEDIA_STREAM.HEIGHT) != null
                                ? new SourceVideoDescription(r.get(MEDIA_STREAM.WIDTH), r.get(MEDIA_STREAM.HEIGHT),
                                        r.get(MEDIA_STREAM.PIXEL_FORMAT), null) : null,
                        r.get(MEDIA_STREAM.SAMPLE_RATE) != null || r.get(MEDIA_STREAM.CHANNELS) != null
                                ? new SourceAudioDescription(r.get(MEDIA_STREAM.SAMPLE_RATE), r.get(MEDIA_STREAM.CHANNELS),
                                        r.get(MEDIA_STREAM.CHANNEL_LAYOUT), r.get(MEDIA_STREAM.SAMPLE_FORMAT),
                                        r.get(MEDIA_STREAM.BIT_DEPTH)) : null,
                        new SourceColorDescription(r.get(MEDIA_STREAM.COLOR_PRIMARIES),
                                r.get(MEDIA_STREAM.COLOR_TRANSFER), r.get(MEDIA_STREAM.COLOR_MATRIX),
                                r.get(MEDIA_STREAM.COLOR_RANGE), r.get(MEDIA_STREAM.HDR_MASTERING_DISPLAY_REF),
                                r.get(MEDIA_STREAM.HDR_CONTENT_LIGHT_REF)),
                        r.get(MEDIA_STREAM.CONTAINER_STREAM_DESCRIPTION)));
    }

    @Override
    public void deleteByMediaAssetId(MediaAssetId mediaAssetId) {
        dsl.deleteFrom(MEDIA_STREAM)
                .where(MEDIA_STREAM.MEDIA_ASSET_ID.eq(mediaAssetId.value()))
                .execute();
    }
}
