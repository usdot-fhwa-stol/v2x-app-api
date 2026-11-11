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
    private static final Map<String, String> DDOT = Map.of("agency", "DDOT");

    private static final List<String> AV_SUBSCRIBE_TEMPLATES = List.of(
            "PSM/unsigned/J2735/{agency}/@",
            "BSM/unsigned/J2735/{agency}/@",
            "SDSM/unsigned/J2735/{agency}/@",
            "SPAT/unsigned/J2735/{agency}/@",
            "MAP/unsigned/J2735/{agency}/@",
            "TIM/unsigned/J2735/@");

    @Test
    void resolvesAvTemplatesWithAgency() {
        assertEquals(List.of(
                "PSM/unsigned/J2735/DDOT/@",
                "BSM/unsigned/J2735/DDOT/@",
                "SDSM/unsigned/J2735/DDOT/@",
                "SPAT/unsigned/J2735/DDOT/@",
                "MAP/unsigned/J2735/DDOT/@",
                "TIM/unsigned/J2735/@"),
                TopicTemplateResolver.resolve(AV_SUBSCRIBE_TEMPLATES, DDOT, SPEC));
    }

    @Test
    void changingTheVariableChangesEveryTopic() {
        List<String> resolved = TopicTemplateResolver.resolve(AV_SUBSCRIBE_TEMPLATES, Map.of("agency", "MDOT"), SPEC);

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
        assertEquals("v1/DDOT/@/east", TopicTemplateResolver.resolve("v1/{agency}/@/{region}",
                Map.of("agency", "DDOT", "region", "east"), SPEC));
    }

    @Test
    void missingVariableFails() {
        UnresolvedTopicVariableException e = assertThrows(UnresolvedTopicVariableException.class,
                () -> TopicTemplateResolver.resolve("MAP/unsigned/J2735/{agency}/@", Map.of(), SPEC));
        assertEquals("agency", e.getVariable());
        assertThrows(UnresolvedTopicVariableException.class,
                () -> TopicTemplateResolver.resolve("MAP/unsigned/J2735/{agency}/@", null, SPEC));
    }

    @Test
    void invalidVariableValueFails() {
        assertThrows(IllegalArgumentException.class,
                () -> TopicTemplateResolver.resolve("MAP/unsigned/J2735/{agency}/@", Map.of("agency", "DD/OT"), SPEC));
    }

    @Test
    void listsPlaceholderNamesInOrder() {
        assertEquals(Set.of("agency"), TopicTemplateResolver.placeholderNames(AV_SUBSCRIBE_TEMPLATES));
        assertEquals(List.of("agency", "region"), List.copyOf(TopicTemplateResolver.placeholderNames(
                List.of("a/{agency}/@", "b/{region}/{agency}"))));
    }
}
