package usdot.v2x.app.api.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import usdot.v2x.app.api.models.dto.provider.GeohashTopicSpec;
import usdot.v2x.app.api.provider.TopicTemplateResolver.UnresolvedTopicVariableException;

class TopicTemplateResolverTest {

    private static final GeohashTopicSpec SPEC = new GeohashTopicSpec();

    /** Default AV agencies: publish to DDOT, subscribe to DDOT and Raven. */
    private static final Map<String, List<String>> AV_DEFAULTS = Map.of(
            "publishAgency", List.of("DDOT"),
            "subscribeAgency", List.of("DDOT", "Raven"));

    private static final List<String> AV_PUBLISH_TEMPLATES = List.of(
            "PSM/unsigned/J2735/{publishAgency}/@",
            "BSM/unsigned/J2735/{publishAgency}/@");

    private static final List<String> AV_SUBSCRIBE_TEMPLATES = List.of(
            "PSM/unsigned/J2735/{subscribeAgency}/@",
            "BSM/unsigned/J2735/{subscribeAgency}/@",
            "SDSM/unsigned/J2735/{subscribeAgency}/@",
            "SPAT/unsigned/J2735/{subscribeAgency}/@",
            "MAP/unsigned/J2735/{subscribeAgency}/@",
            "TIM/unsigned/J2735/@");

    @Test
    void resolvesSingleValueToOneLevel() {
        assertEquals(List.of(
                "PSM/unsigned/J2735/DDOT/@",
                "BSM/unsigned/J2735/DDOT/@"),
                TopicTemplateResolver.resolve(AV_PUBLISH_TEMPLATES, AV_DEFAULTS, SPEC));
    }

    @Test
    void joinsMultipleValuesAsAlternatives() {
        assertEquals(List.of(
                "PSM/unsigned/J2735/DDOT|Raven/@",
                "BSM/unsigned/J2735/DDOT|Raven/@",
                "SDSM/unsigned/J2735/DDOT|Raven/@",
                "SPAT/unsigned/J2735/DDOT|Raven/@",
                "MAP/unsigned/J2735/DDOT|Raven/@",
                "TIM/unsigned/J2735/@"),
                TopicTemplateResolver.resolve(AV_SUBSCRIBE_TEMPLATES, AV_DEFAULTS, SPEC));
    }

    @Test
    void keepsValueOrder() {
        assertEquals("MAP/unsigned/J2735/Raven|DDOT/@", TopicTemplateResolver.resolve(
                "MAP/unsigned/J2735/{subscribeAgency}/@", Map.of("subscribeAgency", List.of("Raven", "DDOT")), SPEC));
    }

    @Test
    void changingTheVariableChangesEveryTopic() {
        List<String> resolved = TopicTemplateResolver.resolve(AV_SUBSCRIBE_TEMPLATES,
                Map.of("subscribeAgency", List.of("MDOT")), SPEC);

        assertEquals("MAP/unsigned/J2735/MDOT/@", resolved.get(4));
        assertTrue(resolved.stream().noneMatch(topic -> topic.contains("DDOT")));
    }

    @Test
    void leavesTemplatesWithoutPlaceholdersUntouched() {
        String template = "v1/g32/@/31|19|18|41";

        assertSame(template, TopicTemplateResolver.resolve(template, Map.of(), SPEC));
    }

    @Test
    void resolvesMultiplePlaceholders() {
        assertEquals("v1/DDOT/@/east|west", TopicTemplateResolver.resolve("v1/{agency}/@/{region}",
                Map.of("agency", List.of("DDOT"), "region", List.of("east", "west")), SPEC));
    }

    @Test
    void missingOrEmptyVariableFails() {
        String template = "MAP/unsigned/J2735/{subscribeAgency}/@";

        UnresolvedTopicVariableException e = assertThrows(UnresolvedTopicVariableException.class,
                () -> TopicTemplateResolver.resolve(template, Map.of(), SPEC));
        assertEquals("subscribeAgency", e.getVariable());
        assertThrows(UnresolvedTopicVariableException.class,
                () -> TopicTemplateResolver.resolve(template, null, SPEC));
        assertThrows(UnresolvedTopicVariableException.class,
                () -> TopicTemplateResolver.resolve(template, Map.of("subscribeAgency", List.of()), SPEC));
    }

    @Test
    void invalidValueFails() {
        assertThrows(IllegalArgumentException.class, () -> TopicTemplateResolver.resolve(
                "MAP/unsigned/J2735/{subscribeAgency}/@", Map.of("subscribeAgency", List.of("DDOT", "Ra/ven")), SPEC));
    }

    @Test
    void duplicateValueFails() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TopicTemplateResolver.resolve(
                "MAP/unsigned/J2735/{subscribeAgency}/@", Map.of("subscribeAgency", List.of("DDOT", "DDOT")), SPEC));
        assertTrue(e.getMessage().contains("more than once"));
    }

    @Test
    void validateValuesRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateResolver.validateValues("publishAgency", List.of(), SPEC));
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateResolver.validateValues("publishAgency", null, SPEC));
    }

    @Test
    void listsPlaceholderNamesInOrder() {
        assertEquals(Set.of("subscribeAgency"), TopicTemplateResolver.placeholderNames(AV_SUBSCRIBE_TEMPLATES));
        assertEquals(List.of("agency", "region"), List.copyOf(TopicTemplateResolver.placeholderNames(
                List.of("a/{agency}/@", "b/{region}/{agency}"))));
    }
}
