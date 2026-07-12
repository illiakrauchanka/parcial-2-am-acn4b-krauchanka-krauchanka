package com.davinci.medtraveler.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DrugInfoRepoParseTest {

    @Test public void parsesGenericName() {
        String json = "{\"results\":[{\"openfda\":{\"generic_name\":[\"IBUPROFEN\"],"
                + "\"brand_name\":[\"ADVIL\"]}}]}";
        assertEquals("ibuprofen", DrugInfoRepo.parseActiveIngredient(json));
    }

    @Test public void fallsBackToActiveIngredientField() {
        String json = "{\"results\":[{\"active_ingredient\":"
                + "[\"Active ingredient: Diphenhydramine HCl 25 mg\"],\"openfda\":{}}]}";
        assertEquals("active ingredient: diphenhydramine hcl 25 mg",
                DrugInfoRepo.parseActiveIngredient(json));
    }

    @Test public void emptyResultsGivesNull() {
        assertNull(DrugInfoRepo.parseActiveIngredient("{\"results\":[]}"));
    }

    @Test public void errorPayloadGivesNull() {
        assertNull(DrugInfoRepo.parseActiveIngredient(
                "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"No matches found!\"}}"));
    }

    @Test public void malformedJsonGivesNull() {
        assertNull(DrugInfoRepo.parseActiveIngredient("not json at all"));
        assertNull(DrugInfoRepo.parseActiveIngredient(null));
        assertNull(DrugInfoRepo.parseActiveIngredient(""));
    }

    @Test public void missingOpenfdaBlockAndNoActiveIngredientGivesNull() {
        assertNull(DrugInfoRepo.parseActiveIngredient("{\"results\":[{\"id\":\"x\"}]}"));
    }
}
