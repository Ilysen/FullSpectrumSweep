package ilysen.fullspectrumsweep.campaign.abilities;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.loading.CampaignPingSpec;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import lunalib.lunaSettings.LunaSettings;

import org.apache.log4j.Logger;

public class FullSpectrumSweepAbility extends BaseDurationAbility {
	public static final String FRONT_END_TEXT = "Full-spectrum sweep";
	public static final String FLAG_NAME = "$ilysen_fullSpectrumSweep_didFssInSystem";
	public static final float DETECTABILITY_RANGE_BONUS = 5000f;
	public static final String COMMODITY_ID = "volatiles";
	public static final int COMMODITY_PER_USE = 1;

	protected boolean isPerformingScan = false;
	public boolean hasScannedCurSystem = false;
	public boolean systemComplete = false;
	protected boolean isInHyperspace = false;

	private Map<String, List<SectorEntityToken>> _cachedEntities;
	private static final Logger log = Global.getLogger(FullSpectrumSweepAbility.class);
	private int _spookyTextTicks = 0;
	private String _spookyText;

	public FullSpectrumSweepAbility() { }

	@Override
	public String getSpriteName() {
		CampaignFleetAPI fleet = getFleet();
		if (fleet != null) {
			if (Misc.isInAbyss(fleet))
				return Global.getSettings().getSpriteName("abilities", "fss_blank");
			if (hasScannedCurSystem) {
				return Global.getSettings().getSpriteName("abilities", systemComplete ? "fss_complete" : "fss_remaining");
			}
		}
		return super.getSpriteName();
	}

	@Override
	public void pressButton() {
		if (!isUsable() || turnedOn)
			return;
		CampaignFleetAPI fleet = getFleet();
		if (fleet == null)
			return;
		int commodityCost = GetCommodityCost();
		if (fleet.getCargo().getCommodityQuantity(COMMODITY_ID) < commodityCost && !Global.getSettings().isDevMode()) {
      CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(COMMODITY_ID);
      fleet.addFloatingText("Out of " + spec.getName().toLowerCase(), Misc.setAlpha(this.entity.getIndicatorColor(), 255), 0.5F);
			Global.getSoundPlayer().playUISound("ui_neutrino_detector_off", 1f, 1f);
			return;
		}
		super.pressButton();
	}

	@Override
	protected void activateImpl() {
		log.info("Activating");
		if (entity.isInCurrentLocation()) {
			if (entity.getVisibilityLevelToPlayerFleet() != VisibilityLevel.NONE) {
				CampaignPingSpec custom = new CampaignPingSpec();
				custom.setUseFactionColor(true);
				custom.setWidth(7);
				custom.setRange(2000);
				custom.setDuration(2f);
				custom.setNum(3);
				custom.setDelay(0.25f);
				custom.setSounds(Arrays.asList("default_campaign_ping", "default_campaign_ping", "default_campaign_ping"));
				Global.getSector().addPing(entity, custom);
			}
			isPerformingScan = true;
			int commodityCost = GetCommodityCost();
			log.info("Volatiles to consume: " + commodityCost);
			if (commodityCost > 0)
				entity.getCargo().removeCommodity(COMMODITY_ID, commodityCost);
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
			RescanSystem(fleet.getContainingLocation());
			GenerateMessage(fleet.getContainingLocation());
			isPerformingScan = false;
		}
	}

	@Override
	protected String getActivationText() {
		return FRONT_END_TEXT;
	}

	@Override
	protected void cleanupImpl() {
		log.info("Cleaning up");
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
		if (fleet == null || isInHyperspace || fleet.isInHyperspaceTransition())
			return false;
		StarSystemAPI system = fleet.getStarSystem();
		return system != null && !hasScannedCurSystem;
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
		CampaignFleetAPI fleet = getFleet();
		if (fleet == null)
			return;
		Color highlight = Misc.getHighlightColor();
		Color negative = Misc.getNegativeHighlightColor();
		Color gray = Misc.getGrayColor();
		float _padding = 10.0F;

		if (!Global.CODEX_TOOLTIP_MODE) {
			tooltip.addTitle(spec.getName());
			if (Misc.isInAbyss(getFleet())) { // Special handling in abyssal hyperspace; glitchy text and blank icon
				String spooky = "";
				if (_spookyTextTicks > 0) {
					_spookyTextTicks--;
					if (_spookyText.isBlank()) {
						switch (Misc.random.nextInt(4)) {
							case 1:
								_spookyText = "WARN: drive bubble pressure exceeds safe tolerance by (10^2 * 3.6821); recommend sensor shutdown";
								break;
							case 2:
								_spookyText = "WARN: nav error overflow, unable to determine position (last location ...)";
								break;
							case 3:
								_spookyText = "WARN: invalid sensor input, unsupported data type (x" + Misc.random.nextInt(1111, 99999) + ")";
								break;
							default:
								_spookyText = "WARN: interface request refused due to uncertified source";
								break;
						}
					}
					tooltip.addPara("%s", _padding, negative, _spookyText);
				} else {
					for (int i = 0; i < Misc.random.nextInt(4, 45); i++) {
						spooky += "?";
					}
					tooltip.addPara(spooky, _padding);
					if (Misc.random.nextInt(200) == 1) {
							_spookyTextTicks = Misc.random.nextInt(10, 40);
							_spookyText = "";
					}
				}
				return;
			}
		} else {
			tooltip.addSpacer(_padding);
		}

		if (!Global.CODEX_TOOLTIP_MODE && hasScannedCurSystem) {
			if (systemComplete) {
				tooltip.addPara("%s", _padding, Misc.getPositiveHighlightColor(), "All detectable objects in this system have been discovered.");
				tooltip.addPara("%s", _padding, gray, "This does not track if objects have been investigated, only if they have been seen at least once.");
				tooltip.addPara("%s", _padding, gray, "Fleets, cargo pods, and unstable debris do not appear here and may still be undiscovered.");
				return;
			} else {
				tooltip.addPara("Undiscovered objects in this system:", _padding);
				List<SectorEntityToken> debris = _cachedEntities.get(Tags.DEBRIS_FIELD);
				List<SectorEntityToken> lowSources = _cachedEntities.get(Tags.NEUTRINO_LOW);
				List<SectorEntityToken> medSources = _cachedEntities.get(Tags.NEUTRINO);
				List<SectorEntityToken> highSources = _cachedEntities.get(Tags.NEUTRINO_HIGH);
				List<SectorEntityToken> stations = _cachedEntities.get(Tags.STATION);
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
			int volatilesConsumption = GetCommodityCost();
			if (volatilesConsumption > 0) {
				tooltip.addPara(
						"Consumes %s unit" + (volatilesConsumption == 1 ? "" : "s") + " of " + COMMODITY_ID + " per use.",
						_padding,
						Misc.getHighlightColor(),
						volatilesConsumption + "");
			}
			tooltip.addPara("*2000 units = 1 map grid cell", gray, _padding);
			addIncompatibleToTooltip(tooltip, expanded);
		}
		if (!Global.CODEX_TOOLTIP_MODE) {
			if (isInHyperspace) {
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

	// Runs and caches GetAllUndiscoveredEntities on the provided loc, and re-calculates conditionals (systemComplete, etc).
	// We run this whenever the player changes location or discovers a new object.
	// Theoretically this could just be determined every frame, but it's way cleaner to only rerun it when necessary.
	public void RescanSystem(LocationAPI loc)
	{
		log.info("Rescanning: " + loc.getName());
		isInHyperspace = loc.isHyperspace();
		if (loc.isHyperspace()) {
			log.info("Entered hyperspace. Ending logic here.");
			_cachedEntities = null;
			systemComplete = false;
			hasScannedCurSystem = false;
			return;
		}
		_cachedEntities = GetAllUndiscoveredEntities(loc);
		systemComplete = true; // Set this with a default of true...
		for (Map.Entry<String, List<SectorEntityToken>> entry : _cachedEntities.entrySet()) {
			log.info(entry.getKey() + " contains " + entry.getValue().size() + " entries");
			if (!entry.getValue().isEmpty() && systemComplete) {
				systemComplete = false; // ...and set it to false if there are entities that we haven't discovered
				break;
			}
		}
		hasScannedCurSystem = loc.getMemoryWithoutUpdate().contains(FLAG_NAME) || (Global.getSettings().getModManager().isModEnabled("lunalib") && LunaSettings.getBoolean("ilysen-fullspectrumsweep", "PassiveMode"));
		log.info("Has scanned system:" + (hasScannedCurSystem ? "true" : "false"));
		log.info("System complete:" + (systemComplete ? "true" : "false"));
		log.info("Rescan complete.");
	}

	public void GenerateMessage(LocationAPI loc)
	{
		int totalSize = 0;
		for (List<SectorEntityToken> subset : _cachedEntities.values()) {
			totalSize += subset.size();
		}
		String objectText = totalSize + " signature" + (totalSize == 1 ? "" : "s") + " detected";
		String title = FRONT_END_TEXT + ": " + objectText;
		
		MessageIntel intel = new MessageIntel(title, Misc.getBasePlayerColor(), new String[] { totalSize + "" }, Misc.getHighlightColor());
		intel.setIcon(getSpriteName());
		intel.setSound("ui_discovered_entity");
		
		Global.getSector().getCampaignUI().addMessage(intel);
	}

	private int GetCommodityCost() {
		if (Global.getSettings().getModManager().isModEnabled("lunalib"))
			return LunaSettings.getInt("ilysen-fullspectrumsweep", "VolatilesCost");
		return COMMODITY_PER_USE;
	}
}
