package usdot.v2x.app.api.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import usdot.v2x.app.api.models.dto.provider.GeohashTopicSpec;

class TopicTemplateValidatorTest {

    private static final GeohashTopicSpec DEFAULT_SPEC = new GeohashTopicSpec();

    private static final List<String> NMI_PUBLISH = List.of("v1/g32/@/20|32");
    private static final List<String> NMI_SUBSCRIBE = List.of("v1/g32/@/31|19|18|41");

    private static final List<String> AV_PUBLISH = List.of(
            "PSM/unsigned/J2735/{agency}/@",
            "BSM/unsigned/J2735/{agency}/@");
    private static final List<String> AV_SUBSCRIBE = List.of(
            "PSM/unsigned/J2735/{agency}/@",
            "BSM/unsigned/J2735/{agency}/@",
            "SDSM/unsigned/J2735/{agency}/@",
            "SPAT/unsigned/J2735/{agency}/@",
            "MAP/unsigned/J2735/{agency}/@",
            "TIM/unsigned/J2735/@");

    @Test
    void defaultSpecMatchesAgreedSubscribeBehaviour() {
        assertEquals("@", DEFAULT_SPEC.getSymbol());
        assertEquals(7, DEFAULT_SPEC.getLevels());
        assertEquals(7, DEFAULT_SPEC.getPublishPrecision());
        assertEquals(7, DEFAULT_SPEC.getSubscribePrecision());
        assertEquals(1, DEFAULT_SPEC.getSubscribeGridRadius());
        assertEquals(9, DEFAULT_SPEC.subscribeCellCount());
    }

    @Test
    void acceptsNmiRule() {
        assertDoesNotThrow(() -> TopicTemplateValidator.validateRule(NMI_PUBLISH, NMI_SUBSCRIBE, DEFAULT_SPEC, 36));
    }

    @Test
    void acceptsAvRuleWithTrailingGeohash() {
        assertDoesNotThrow(() -> TopicTemplateValidator.validateRule(AV_PUBLISH, AV_SUBSCRIBE, DEFAULT_SPEC, 54));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "v1/g32/@/20|32",
            "v1/g32/@/#",
            "v1/g32/@/+",
            "TIM/unsigned/J2735/@",
            "BSM/unsigned/J2735/DDOT/@",
            "BSM/unsigned/J2735/{agency}/@",
            "{prefix}/g32/@/{agency_2}",
            "vzimp/1/GeoRelevance/*/*/Public/j2735_gr|j2735/BSM|TIM/*",
            "#"
    })
    void acceptsValidSubscribeTopics(String topic) {
        assertDoesNotThrow(() -> TopicTemplateValidator.validateSubscribeTopic(topic, DEFAULT_SPEC));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "v1/g32/@/@/20",
            "v1/g32/#/@",
            "v1/g32/a@b/20",
            "v1/g32/@/20|",
            "v1/g32/@/20||32",
            "v1/g32/@/2+0",
            "v1/g32/@/20|#",
            "/v1/g32/@/20",
            "v1/g32/@/20/",
            "v1//g32/@/20",
            " v1/g32/@/20",
            "BSM/unsigned/J2735/{agency/@",
            "BSM/unsigned/J2735/agency}/@",
            "BSM/unsigned/J2735/x{agency}/@",
            "BSM/unsigned/J2735/{}/@",
            "BSM/unsigned/J2735/{1agency}/@",
            "BSM/unsigned/J2735/{agency}|DDOT/@",
            ""
    })
    void rejectsInvalidSubscribeTopics(String topic) {
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSubscribeTopic(topic, DEFAULT_SPEC));
    }

    @ParameterizedTest
    @ValueSource(strings = { "v1/g32/@/+", "v1/g32/@/#", "BSM/*/J2735/@" })
    void rejectsWildcardsInPublishTopics(String topic) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validatePublishTopic(topic, DEFAULT_SPEC));
        assertTrue(e.getMessage().contains("must not contain wildcards"));
    }

    @Test
    void rejectsNullTopic() {
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validatePublishTopic(null, DEFAULT_SPEC));
    }

    @Test
    void reportsEveryInvalidTopicInRule() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateRule(List.of("a/+"), List.of("b/#/c"), DEFAULT_SPEC, null));
        assertTrue(e.getMessage().contains("publish topic 'a/+'"));
        assertTrue(e.getMessage().contains("subscribe topic 'b/#/c'"));
    }

    @Test
    void supportsCustomSymbol() {
        GeohashTopicSpec spec = new GeohashTopicSpec("~", 7, 7, 7, 1);

        assertDoesNotThrow(() -> TopicTemplateValidator.validateSubscribeTopic("v1/g32/~/31", spec));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSubscribeTopic("v1/g32/~/~/31", spec));
    }

    @Test
    void expandsNmiSubscriptionCount() {
        assertEquals(36, TopicTemplateValidator.expandedSubscriptionCount(NMI_SUBSCRIBE, DEFAULT_SPEC));
    }

    @Test
    void expandsAvSubscriptionCount() {
        assertEquals(54, TopicTemplateValidator.expandedSubscriptionCount(AV_SUBSCRIBE, DEFAULT_SPEC));
    }

    @Test
    void expandsTopicsWithoutGeohashOncePerAlternative() {
        // a/b|c/d|e -> 2 x 2 = 4 topics; x/# -> 1 topic
        assertEquals(5, TopicTemplateValidator.expandedSubscriptionCount(
                List.of("a/b|c/d|e", "x/#"), DEFAULT_SPEC));
    }

    @Test
    void gridRadiusChangesCellCount() {
        GeohashTopicSpec singleCell = new GeohashTopicSpec("@", 7, 7, 7, 0);
        GeohashTopicSpec fiveByFive = new GeohashTopicSpec("@", 7, 7, 7, 2);

        assertEquals(4, TopicTemplateValidator.expandedSubscriptionCount(NMI_SUBSCRIBE, singleCell));
        assertEquals(100, TopicTemplateValidator.expandedSubscriptionCount(NMI_SUBSCRIBE, fiveByFive));
    }

    @Test
    void rejectsSubscribeLimitBelowExpandedCount() {
        IllegalArgumentException nmi = assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateRule(NMI_PUBLISH, NMI_SUBSCRIBE, DEFAULT_SPEC, 35));
        assertTrue(nmi.getMessage().contains("36"));

        IllegalArgumentException av = assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateRule(AV_PUBLISH, AV_SUBSCRIBE, DEFAULT_SPEC, 53));
        assertTrue(av.getMessage().contains("54"));
    }

    @Test
    void acceptsPlaceholderInPublishTopic() {
        assertDoesNotThrow(() -> TopicTemplateValidator.validatePublishTopic("BSM/unsigned/J2735/{agency}/@",
                DEFAULT_SPEC));
    }

    @Test
    void placeholderLevelsCountOnce() {
        assertEquals(9, TopicTemplateValidator.expandedSubscriptionCount(
                List.of("TIM/unsigned/J2735/{agency}/@"), DEFAULT_SPEC));
    }

    @ParameterizedTest
    @ValueSource(strings = { "DDOT", "MDOT", "agency-1", "a_b" })
    void acceptsLiteralVariableValues(String value) {
        assertDoesNotThrow(() -> TopicTemplateValidator.validateVariableValue("agency", value, DEFAULT_SPEC));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", " DDOT", "DD/OT", "DDOT|MDOT", "+", "#", "*", "{x}", "D@T" })
    void rejectsNonLiteralVariableValues(String value) {
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateVariableValue("agency", value, DEFAULT_SPEC));
    }

    @Test
    void rejectsNullVariableValue() {
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateVariableValue("agency", null, DEFAULT_SPEC));
    }

    @Test
    void rejectsInvalidSpecs() {
        assertThrows(IllegalArgumentException.class, () -> TopicTemplateValidator.validateSpec(null));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSpec(new GeohashTopicSpec("", 7, 7, 7, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSpec(new GeohashTopicSpec("+", 7, 7, 7, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSpec(new GeohashTopicSpec("a/b", 7, 7, 7, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSpec(new GeohashTopicSpec("{", 7, 7, 7, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSpec(new GeohashTopicSpec("@", 0, 1, 1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSpec(new GeohashTopicSpec("@", 13, 7, 7, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSpec(new GeohashTopicSpec("@", 7, 8, 7, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSpec(new GeohashTopicSpec("@", 7, 7, 8, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSpec(new GeohashTopicSpec("@", 7, 7, 7, 3)));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateValidator.validateSpec(new GeohashTopicSpec("@", 7, 7, 7, -1)));
    }

    @Test
    void acceptsLowerSubscribePrecisionThanLevels() {
        assertDoesNotThrow(() -> TopicTemplateValidator.validateSpec(new GeohashTopicSpec("@", 7, 7, 5, 1)));
    }
}
