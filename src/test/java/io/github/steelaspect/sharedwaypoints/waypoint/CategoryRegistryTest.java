package io.github.steelaspect.sharedwaypoints.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

class CategoryRegistryTest {
	private static CategoryRegistry build(List<String> warnings, CategoryRegistry.Definition... definitions) {
		return CategoryRegistry.fromDefinitions(List.of(definitions), warnings::add);
	}

	@Test
	void defaultsMatchTheOriginalColours() {
		assertEquals(List.of("storage", "farms", "bases", "portals", "other"), CategoryRegistry.DEFAULT.ids());
		assertEquals(11, TestCategories.STORAGE.xaeroColorIndex());
		assertEquals(10, TestCategories.FARMS.xaeroColorIndex());
		assertEquals(6, TestCategories.BASES.xaeroColorIndex());
		assertEquals(13, TestCategories.PORTALS.xaeroColorIndex());
		assertEquals("Purple", TestCategories.PORTALS.xaeroColorName());
		assertEquals(15, TestCategories.OTHER.xaeroColorIndex());
	}

	@Test
	void customCategoriesKeepConfigOrder() {
		List<String> warnings = new ArrayList<>();
		CategoryRegistry registry = build(warnings,
				new CategoryRegistry.Definition("Shops", "Shops", "YELLOW"),
				new CategoryRegistry.Definition("mines", "Mines", "dark_gray"));
		assertEquals(List.of("shops", "mines"), registry.ids(), "ids are lower-cased");
		Category shops = registry.byId("SHOPS").orElseThrow();
		assertEquals(ChatFormatting.YELLOW, shops.color());
		assertEquals(14, shops.xaeroColorIndex());
		assertEquals(0, shops.order());
		assertEquals(1, registry.resolve("mines").order());
		assertTrue(warnings.isEmpty(), warnings.toString());
	}

	@Test
	void invalidEntriesAreSkippedWithAWarning() {
		List<String> warnings = new ArrayList<>();
		CategoryRegistry registry = build(warnings,
				new CategoryRegistry.Definition("add", "Add", "red"), // subcommand
				new CategoryRegistry.Definition("two words", "Bad", "red"), // not one word
				new CategoryRegistry.Definition("pink", "Pink", "bold"), // not a colour
				new CategoryRegistry.Definition("ok", "", "red"), // empty name
				new CategoryRegistry.Definition("ok", "OK", "red"),
				new CategoryRegistry.Definition("ok", "Again", "blue")); // duplicate
		assertEquals(List.of("ok"), registry.ids());
		assertEquals(5, warnings.size(), warnings.toString());
	}

	@Test
	void nothingValidFallsBackToDefaults() {
		List<String> warnings = new ArrayList<>();
		CategoryRegistry registry = build(warnings, new CategoryRegistry.Definition("go", "Go", "red"));
		assertEquals(CategoryRegistry.DEFAULT.ids(), registry.ids());
	}

	@Test
	void unknownIdsGetAGrayStandIn() {
		Category unknown = CategoryRegistry.DEFAULT.resolve("Shops");
		assertEquals("shops", unknown.id());
		assertEquals("Shops", unknown.displayName());
		assertEquals(ChatFormatting.GRAY, unknown.color());
		assertEquals(Integer.MAX_VALUE, unknown.order(), "listed after configured categories");
		assertEquals(TestCategories.OTHER, CategoryRegistry.DEFAULT.resolve(null));
	}

	@Test
	void everyColourNameIsAccepted() {
		assertEquals(16, CategoryRegistry.colorNames().size());
		for (String color : CategoryRegistry.colorNames()) {
			assertEquals(List.of("c"), CategoryRegistry.fromDefinitions(
					List.of(new CategoryRegistry.Definition("c", "C", color)), warning -> {
					}).ids(), color);
		}
	}
}
