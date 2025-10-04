package ilysen.fullspectrumsweep.campaign.abilities;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class FullSpectrumSweepAbility extends BaseDurationAbility {
	public static final float DETECTABILITY_RANGE_BONUS = 5000f;
	public static final float COMBAT_READINESS_HIT_MULT = 0.05f;
	protected boolean performed = false;
	protected boolean hasScannedCurSystem = false;
	protected boolean systemComplete = false;

	public FullSpectrumSweepAbility() {
	}

	@Override
	public String getSpriteName() {
		CampaignFleetAPI fleet = getFleet();
		if (fleet != null) {
			if (Misc.isInAbyss(getFleet()))
				return Global.getSettings().getSpriteName("abilities", "fss_blank");
			else {
				if (systemComplete) {
					return Global.getSettings().getSpriteName("abilities", "fss_complete");
				} else if (hasScannedCurSystem) {
					return Global.getSettings().getSpriteName("abilities", "fss_remaining");
				}
			}
		}
		return super.getSpriteName();
	}

	@Override
	protected void activateImpl() {
		if (this.entity.isInCurrentLocation()) {
			/*
			 * SectorEntityToken.VisibilityLevel level =
			 * entity.getVisibilityLevelToPlayerFleet();
			 * if (level != VisibilityLevel.NONE) {
			 * Global.getSector().addPing(entity, "fss");
			 * }
			 */
			this.performed = false;
		}
	}

	@Override
	protected void applyEffect(float arg0, float arg1) {
		CampaignFleetAPI fleet = this.getFleet();
		if (fleet == null)
			return;
		fleet.getStats().getFleetwideMaxBurnMod().modifyMult(this.getModId(), 0.0F, "Full-spectrum sweep");
		fleet.getStats().getDetectedRangeMod().modifyFlat(this.getModId(), DETECTABILITY_RANGE_BONUS * level,
				"Full-spectrum sweep");
		fleet.getStats().getAccelerationMult().modifyMult(this.getModId(), 1.0f + 3.0f * level);
		if (!this.performed && level >= 1.0F) {
			fleet.getStarSystem().getMemoryWithoutUpdate().set("$fss_didFssInSystem", true);
			LocationAPI loc = fleet.getContainingLocation();
			Map<String, List<SectorEntityToken>> scannedEntities = GetAllUndiscoveredEntities(loc);
			int totalSize = 0;
			for (List<SectorEntityToken> subset : scannedEntities.values()) {
				totalSize += subset.size();
			}
			if (totalSize == 0)
				Global.getSector().getCampaignUI().addMessage("Full-spectrum sweep complete; no undiscovered objects detected");
			else {
				Global.getSector().getCampaignUI().addMessage("Full-spectrum sweep complete; detected %s undiscovered object%s",
						Misc.getTextColor(),
						totalSize == 0 ? "no" : totalSize + "",
						totalSize == 1 ? "" : "s",
						Misc.getHighlightColor(),
						Misc.getTextColor());
			}
			this.performed = true;
		}
	}

	@Override
	protected String getActivationText() {
		return "Full-spectrum sweep";
	}

	@Override
	protected void cleanupImpl() {
		CampaignFleetAPI fleet = this.getFleet();
		if (fleet != null) {
			fleet.getStats().getDetectedRangeMod().unmodify(this.getModId());
			fleet.getStats().getFleetwideMaxBurnMod().unmodify(this.getModId());
			fleet.getStats().getAccelerationMult().unmodify(this.getModId());
		}
	}

	@Override
	protected void deactivateImpl() {
		cleanupImpl();
	}

	@Override
	public boolean isUsable() {
		if (!super.isUsable())
			return false;
		CampaignFleetAPI fleet = this.getFleet();
		if (fleet == null || fleet.isInHyperspace() || fleet.isInHyperspaceTransition())
			return false;
		StarSystemAPI system = fleet.getStarSystem();
		return system != null && !system.getMemoryWithoutUpdate().contains("$fss_didFssInSystem");
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
		CampaignFleetAPI fleet = this.getFleet();
		if (fleet == null)
			return;
		StarSystemAPI system = fleet.getStarSystem();
		float padding = 10.0F;
		Color highlight = Misc.getHighlightColor();
		Color negative = Misc.getNegativeHighlightColor();
		Color gray = Misc.getGrayColor();

		if (!Global.CODEX_TOOLTIP_MODE) {
			tooltip.addTitle(spec.getName());
			if (Misc.isInAbyss(getFleet())) { // Special handling in abyssal hyperspace; glitchy text and blank icon
				String spooky = "";
				if (Misc.random.nextInt(100) == 1) {
					switch (Misc.random.nextInt(5)) {
						case 1:
							tooltip.addPara("%s", padding, negative,
									"WARN: drive bubble pressure exceeds safe tolerance by (10^2 * 3.6821); recommend sensor shutdown");
							break;
						case 2:
							tooltip.addPara("", padding, negative, "WARN: sensor error overflow");
							break;
						case 3:
							for (int i = 0; i < Misc.random.nextInt(5); i++) {
								tooltip.addPara("%s", padding, negative, "WARN: invalid input");
							}
							break;
						case 4:
							tooltip.addPara("%s", padding, negative, "WARN: ambient temperature exceeds 200C");
							break;
						default:
							tooltip.addPara("%s", padding, negative, "WARN: connection lost");
							break;
					}
				} else {
					for (int i = 0; i < 20; i++) {
						spooky += (char) (Misc.random.nextInt('@' - '!'));
					}
					tooltip.addPara(spooky, padding);
				}
				return;
			}
		} else {
			tooltip.addSpacer(padding);
		}

		if (!Global.CODEX_TOOLTIP_MODE && system != null
				&& system.getMemoryWithoutUpdate().contains("$fss_didFssInSystem")) {
			hasScannedCurSystem = true;
			LocationAPI loc = fleet.getContainingLocation();
			Map<String, List<SectorEntityToken>> scannedEntities = GetAllUndiscoveredEntities(loc);
			int totalSize = 0;
			for (List<SectorEntityToken> subset : scannedEntities.values()) {
				totalSize += subset.size();
			}
			if (totalSize == 0) {
				tooltip.addPara("%s", padding, Misc.getPositiveHighlightColor(),
						"All objects in this system have been discovered.");
				return;
			} else {
				tooltip.addPara("Undiscovered objects in this system:",
						padding);
				List<SectorEntityToken> debris = scannedEntities.get(Tags.DEBRIS_FIELD);
				List<SectorEntityToken> lowSources = scannedEntities.get(Tags.NEUTRINO_LOW);
				List<SectorEntityToken> medSources = scannedEntities.get(Tags.NEUTRINO);
				List<SectorEntityToken> highSources = scannedEntities.get(Tags.NEUTRINO_HIGH);
				List<SectorEntityToken> stations = scannedEntities.get(Tags.STATION);
				if (!debris.isEmpty())
					tooltip.addPara("    Stable debris fields: %s", 3f, highlight, debris.size() + "");
				if (!lowSources.isEmpty())
					tooltip.addPara("    Weak signals: %s", 3f, highlight, lowSources.size() + "");
				if (!medSources.isEmpty())
					tooltip.addPara("    Strong signals: %s", 3f, highlight, medSources.size() + "");
				if (!highSources.isEmpty())
					tooltip.addPara("    Powerful signals: %s", 3f, highlight, highSources.size() + "");
				if (!stations.isEmpty())
					tooltip.addPara("    %s", 3f, highlight,
							"At least one active station");
			}
		} else {
			hasScannedCurSystem = false;
			tooltip.addPara(
					"Sweeps the electromagnetic spectrum to detect ambient garbage noise, radio interference, and other telltale signs of human-made artifacts across a star system. This detects the number of undiscovered objects present in the area, but not their specific nature or their exact locations.",
					padding);
			tooltip.addPara(
					"Discovered objects are excluded from the scan. Additionally, some phenomena - including cargo pods, unstable debris, and fleets - cannot be detected at all.",
					padding);
			tooltip.addPara(
					"Any given star system only needs to be scanned one time. Afterwards, an up-to-date summary for the current system can be viewed at any time through this tooltip.",
					padding);
			tooltip.addPara(
					"Increases the range at which the fleet can be detected by %s* units and brings the fleet to a near-stop as drives are powered down to reduce interference.",
					10f,
					Misc.getHighlightColor(),
					new String[] {
							Misc.getRoundedValueMaxOneAfterDecimal(DETECTABILITY_RANGE_BONUS)
					});
		}
		if (!Global.CODEX_TOOLTIP_MODE) {
			if (fleet.isInHyperspace()) {
				tooltip.addPara("Can not be used in hyperspace.", negative, padding);
			} else if (fleet.getStarSystem() == null) {
				tooltip.addPara("Must be used in a star system.", negative, padding);
			}
		}
		tooltip.addPara("*2000 units = 1 map grid cell", gray, padding);
		addIncompatibleToTooltip(tooltip, expanded);
	}

	public static boolean ShouldCullEntry(SectorEntityToken entity) {
		return !entity.isDiscoverable() || entity.hasTag(Tags.EXPIRES);
	}

	public static Map<String, List<SectorEntityToken>> GetAllUndiscoveredEntities(LocationAPI loc) {
		List<String> tagList = new ArrayList<String>();
		tagList.add(Tags.DEBRIS_FIELD);
		tagList.add(Tags.NEUTRINO_LOW);
		tagList.add(Tags.NEUTRINO);
		tagList.add(Tags.NEUTRINO_HIGH);
		tagList.add(Tags.STATION);
		Map<String, List<SectorEntityToken>> entities = new HashMap<>();
		for (String tag : tagList) {
			List<SectorEntityToken> matching = loc.getEntitiesWithTag(tag);
			matching.removeIf(x -> ShouldCullEntry((x)));
			entities.put(tag, matching);
		}
		return entities;
	}
}
