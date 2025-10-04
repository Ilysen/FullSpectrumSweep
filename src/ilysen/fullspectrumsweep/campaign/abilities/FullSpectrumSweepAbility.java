package ilysen.fullspectrumsweep.campaign.abilities;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.impl.campaign.ids.Pings;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class FullSpectrumSweepAbility extends BaseDurationAbility {
	public static final String FRONT_END_TEXT = "Full-spectrum sweep";
	public static final String FLAG_NAME = "$ilysen_fullSpectrumSweep_didFssInSystem";
	public static final float DETECTABILITY_RANGE_BONUS = 5000f;

	protected boolean isPerformingScan = false;
	protected boolean hasScannedCurSystem = false;
	protected boolean systemComplete = false;

	private float _padding = 10f;

	public FullSpectrumSweepAbility() { }

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
		if (entity.isInCurrentLocation()) {
			if (entity.getVisibilityLevelToPlayerFleet() != VisibilityLevel.NONE)
				Global.getSector().addPing(entity, Pings.REMOTE_SURVEY);
			isPerformingScan = true;
		}
	}

	@Override
	protected void applyEffect(float arg0, float arg1) {
		CampaignFleetAPI fleet = getFleet();
		if (fleet == null)
			return;
		fleet.getStats().getFleetwideMaxBurnMod().modifyMult(getModId(), 0f, FRONT_END_TEXT);
		fleet.getStats().getDetectedRangeMod().modifyFlat(getModId(), DETECTABILITY_RANGE_BONUS * level, FRONT_END_TEXT);
		fleet.getStats().getAccelerationMult().modifyMult(getModId(), 1f + (3f * level));
		if (isPerformingScan && level >= 1f) {
			fleet.getStarSystem().getMemoryWithoutUpdate().set(FLAG_NAME, true);
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
			isPerformingScan = false;
		}
	}

	@Override
	protected String getActivationText() {
		return FRONT_END_TEXT;
	}

	@Override
	protected void cleanupImpl() {
		CampaignFleetAPI fleet = getFleet();
		if (fleet != null) {
			fleet.getStats().getDetectedRangeMod().unmodify(getModId());
			fleet.getStats().getFleetwideMaxBurnMod().unmodify(getModId());
			fleet.getStats().getAccelerationMult().unmodify(getModId());
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
		CampaignFleetAPI fleet = getFleet();
		if (fleet == null || fleet.isInHyperspace() || fleet.isInHyperspaceTransition())
			return false;
		StarSystemAPI system = fleet.getStarSystem();
		return system != null && !system.getMemoryWithoutUpdate().contains(FLAG_NAME);
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
		CampaignFleetAPI fleet = getFleet();
		if (fleet == null)
			return;
		StarSystemAPI system = fleet.getStarSystem();
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
							tooltip.addPara("%s", _padding, negative,
									"WARN: drive bubble pressure exceeds safe tolerance by (10^2 * 3.6821); recommend sensor shutdown");
							break;
						case 2:
							tooltip.addPara("", _padding, negative, "WARN: sensor error overflow");
							break;
						case 3:
							for (int i = 0; i < Misc.random.nextInt(5); i++) {
								tooltip.addPara("%s", _padding, negative, "WARN: invalid input");
							}
							break;
						case 4:
							tooltip.addPara("%s", _padding, negative, "WARN: ambient temperature exceeds 200C");
							break;
						default:
							tooltip.addPara("%s", _padding, negative, "WARN: connection lost");
							break;
					}
				} else {
					for (int i = 0; i < 20; i++) {
						spooky += (char) (Misc.random.nextInt('@' - '!'));
					}
					tooltip.addPara(spooky, _padding);
				}
				return;
			}
		} else {
			tooltip.addSpacer(_padding);
		}

		if (!Global.CODEX_TOOLTIP_MODE && system != null
				&& system.getMemoryWithoutUpdate().contains(FLAG_NAME)) {
			hasScannedCurSystem = true;
			LocationAPI loc = fleet.getContainingLocation();
			Map<String, List<SectorEntityToken>> scannedEntities = GetAllUndiscoveredEntities(loc);
			int totalSize = 0;
			for (List<SectorEntityToken> subset : scannedEntities.values()) {
				totalSize += subset.size();
			}
			if (totalSize == 0) {
				tooltip.addPara("%s", _padding, Misc.getPositiveHighlightColor(),
						"All objects in this system have been discovered.");
				return;
			} else {
				tooltip.addPara("Undiscovered objects in this system:",
						_padding);
				List<SectorEntityToken> debris = scannedEntities.get(Tags.DEBRIS_FIELD);
				List<SectorEntityToken> lowSources = scannedEntities.get(Tags.NEUTRINO_LOW);
				List<SectorEntityToken> medSources = scannedEntities.get(Tags.NEUTRINO);
				List<SectorEntityToken> highSources = scannedEntities.get(Tags.NEUTRINO_HIGH);
				List<SectorEntityToken> stations = scannedEntities.get(Tags.STATION);
				if (!debris.isEmpty())
					tooltip.addPara("    Debris fields: %s", 3f, highlight, debris.size() + "");
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
					"Calibrates the fleet's active sensor network to search for garbage noise, abnormal emissions, and other telltale signs of human-made artifacts. This detects the number of foreign objects present in a given star system, but does not discern their specific nature or their exact locations.",
					_padding);
			tooltip.addPara(
					"Objects that have already been discovered are excluded from the sweep. Additionally, some phenomena - including cargo pods, unstable debris, and any fleets other than your own - cannot be detected at all.",
					_padding);
			tooltip.addPara(
					"This ability only needs to be used one time for any given system. Afterwards, this tooltip will provide an up-to-date summary while within that system.",
					_padding);
			tooltip.addPara(
					"During activation, increases the range at which the fleet can be detected by %s* units and brings the fleet to a near-stop as drives are powered down to reduce interference.",
					_padding,
					Misc.getHighlightColor(),
					Misc.getRoundedValueMaxOneAfterDecimal(DETECTABILITY_RANGE_BONUS));
			tooltip.addPara("*2000 units = 1 map grid cell", gray, _padding);
			addIncompatibleToTooltip(tooltip, expanded);
		}
		if (!Global.CODEX_TOOLTIP_MODE) {
			if (fleet.isInHyperspace()) {
				tooltip.addPara("Can not be used in hyperspace.", negative, _padding);
			} else if (fleet.getStarSystem() == null) {
				tooltip.addPara("Must be used in a star system.", negative, _padding);
			}
		}
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
