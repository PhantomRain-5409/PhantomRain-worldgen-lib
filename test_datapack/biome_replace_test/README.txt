ptrlib_wg biome replace test datapack
=====================================

Install:
  Copy folder biome_replace_test into:
    <world>/datapacks/
  Then recreate the world (rules apply when WorldStem loads).

Rules in all_types.json (overworld unless noted):
  1) direct_replace: dark_forest -> cherry_grove
  2) direct_replace remove: swamp (omit replacement)
  3) direct_replace tag: #minecraft:is_beach -> snowy_beach
  4) weighted_replace: forest -> plains(9) + mushroom_fields(1), climate-axis split
  5) weighted_infuse: birch_forest parent_weight=7 + weighted children
  6) sub_biome: plains center + patch noise -> sunflower_plains
  7) sub_biome preset: forest near_border -> flower_forest
  8) nether: warped_forest -> crimson_forest

Key schema:
  type: direct_replace | weighted_replace | weighted_infuse | sub_biome
  target: biome id or #tag
  replacement: direct result (omit/empty = remove)
  replacements: [{biome, weight}] for weighted_replace
  subs: [{biome, weight}] for weighted_infuse
  parent_weight: remaining parent area (default 1)
  biome: sub_biome result
  criterion: sub_biome condition; supports all_of/any_of/not/value/deviation/ratio/neighbor/original/primary/patch_noise
  patch_noise: min/max plus optional scale (approximate block size, default 96) and salt
  preset: near_border | near_interior | beachside | oceanside | riverside
  dimension: optional filter

Sub-biome rules are evaluated in data order; the first matching rule wins.
