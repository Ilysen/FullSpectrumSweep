package ilysen.fullspectrumsweep.campaign.abilities;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.abilities.DurationAbilityWithCost2;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class FullSpectrumSweepAbility extends DurationAbilityWithCost2 {
	public static final float DETECTABILITY_RANGE_BONUS = 5000f;
	public static final float COMBAT_READINESS_HIT_MULT = 0.05f;
	protected boolean performed = false;
	protected boolean terminateTooltipEarly = false;
	protected boolean systemComplete = false;

	public FullSpectrumSweepAbility() { }

	@Override
	public String getSpriteName()
	{
		CampaignFleetAPI fleet = getFleet();
		if (fleet != null) {
			if (Misc.isInAbyss(getFleet()))
				return Global.getSettings().getSpriteName("abilities", "fss_blank");
			else {
				if (systemComplete) {
					return Global.getSettings().getSpriteName("abilities", "fss_complete");
				} else if (terminateTooltipEarly) {
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
			Global.getSector().getCampaignUI().getMessageDisplay().addMessage("bruh");
			this.performed = true;
		}
	}

	@Override
	protected String getActivationText() {
		return "Full-spectrum sweep";
	}

	@Override
	public float getFuelCostMult() {
		return 0f;
	}

	@Override
	public float getCRCostMult() {
		return COMBAT_READINESS_HIT_MULT;
	}

	@Override
	public float getActivationAtLowCRShipDamageProbability() {
		return 0f;
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
		if (terminateTooltipEarly)
		{	
			addInitialDescription(tooltip, expanded);
			return;
		}
		super.createTooltip(tooltip, expanded);
		tooltip.addPara(
				"Also increases the range at which the fleet can be detected by %s units, and brings the fleet to a near-stop as drives are powered down to reduce interference.",
				10f,
				Misc.getHighlightColor(),
				new String[] {
						Misc.getRoundedValueMaxOneAfterDecimal(DETECTABILITY_RANGE_BONUS)
				});
		if (!Global.CODEX_TOOLTIP_MODE) {
			CampaignFleetAPI fleet = getFleet();
			if (fleet != null) {
				if (fleet.isInHyperspace()) {
					tooltip.addPara("Can not be used in hyperspace.", Misc.getNegativeHighlightColor(), 10f);
				} else if (fleet.getStarSystem() == null) {
					tooltip.addPara("Must be used in a star system.", Misc.getNegativeHighlightColor(), 10f);
				}
			}
		}
		tooltip.addPara("*2000 units = 1 map grid cell", Misc.getGrayColor(), 10f);
	}

	@Override
	public void addInitialDescription(TooltipMakerAPI tooltip, boolean expanded) {
		CampaignFleetAPI fleet = this.getFleet();
		if (fleet == null)
			return;
		StarSystemAPI system = fleet.getStarSystem();
		Color highlight = Misc.getHighlightColor();
		if (!Global.CODEX_TOOLTIP_MODE) {
			tooltip.addTitle(spec.getName());
			if (Misc.isInAbyss(getFleet())) {
				java.util.Random r = new java.util.Random();
				String spooky = "";
				if (r.nextInt(100) == 1) {
					switch (r.nextInt(5)) {
						case 1:
							tooltip.addPara("%s", 10f, Misc.getNegativeHighlightColor(), "WARN: drive bubble pressure exceeds safe tolerance by (10^2 * 3.6821); recommend sensor shutdown");
							break;
						case 2:
							tooltip.addPara("", 10f, Misc.getNegativeHighlightColor(), "WARN: sensor error overflow");
							break;
						case 3:
							for (int i = 0; i < r.nextInt(5); i++) {
								tooltip.addPara("%s", 10f, Misc.getNegativeHighlightColor(), "WARN: invalid input");
							}
							break;
						case 4:
							tooltip.addPara("%s", 10f, Misc.getNegativeHighlightColor(), "WARN: ambient temperature exceeds 200C");
							break;
						default:
							tooltip.addPara("%s", 10f, Misc.getNegativeHighlightColor(), "WARN: connection lost");
							break;
					}
				} else {
					for (int i = 0; i < 20; i++) {
						spooky += (char)(r.nextInt('@' - '!'));
					}
					tooltip.addPara(spooky, 10f);
				}
				terminateTooltipEarly = true;
				return;
			}
		} else {
			tooltip.addSpacer(-10.0F);
		}
		float pad = 10.0F;
		if (!Global.CODEX_TOOLTIP_MODE && system != null && system.getMemoryWithoutUpdate().contains("$fss_didFssInSystem")) {
			terminateTooltipEarly = true;
			LocationAPI loc = fleet.getContainingLocation();
			List<SectorEntityToken> lowSources = loc.getEntitiesWithTag("neutrino_low");
			List<SectorEntityToken> medSources = loc.getEntitiesWithTag("neutrino");
			List<SectorEntityToken> highSources = loc.getEntitiesWithTag("neutrino_high");
			List<SectorEntityToken> stations = loc.getEntitiesWithTag("station");
			lowSources.removeIf(x -> shouldCullEntry((x)));
			medSources.removeIf(x -> shouldCullEntry((x)));
			highSources.removeIf(x -> shouldCullEntry((x)));
			stations.removeIf(x -> shouldCullEntry((x)));
			if (lowSources.isEmpty() && medSources.isEmpty() && highSources.isEmpty() && stations.isEmpty()) {
					tooltip.addPara("%s", pad, Misc.getPositiveHighlightColor(), "All objects in this system have been discovered.");
					return;
			}
			tooltip.addPara("Full-spectrum sweep has been performed in this system. Undiscovered objects are as follows:", pad);
			if (!lowSources.isEmpty())
				tooltip.addPara("    Faint signals: %s", 3f, highlight, lowSources.size() + "");
			if (!medSources.isEmpty())
				tooltip.addPara("    Steady signals: %s", 3f, highlight, medSources.size() + "");
			if (!highSources.isEmpty())
				tooltip.addPara("    Strong signals: %s", 3f, highlight, highSources.size() + "");
			if (!stations.isEmpty())
				tooltip.addPara("    Significant background noise. %s is present nearby.", 3f, highlight, "At least one active station");
		} else {
			terminateTooltipEarly = false;
			tooltip.addPara(
					"Run a sensor package that detects the number of all undiscovered objects within a star system. This ability only needs to be used once per system; results can then be tracked at any time by viewing this tooltip.",
					pad);
		}
	}

	protected boolean shouldCullEntry(SectorEntityToken entity) {
		return !entity.isDiscoverable();
	}
}
