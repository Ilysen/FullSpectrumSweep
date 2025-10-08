package ilysen.fullspectrumsweep;

import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;

import ilysen.fullspectrumsweep.campaign.abilities.FullSpectrumSweepAbility;
import lunalib.lunaSettings.LunaSettings;

// Transient listener responsible for triggering a system rescan whenever the player changes locations or discovers a new entity.
public class FullSpectrumSweepListener implements CurrentLocationChangedListener, DiscoverEntityListener {
	private static final Logger log = Global.getLogger(FullSpectrumSweepListener.class);

	public FullSpectrumSweepListener() {
		//log.info("Listener initialized");
	}

	@Override
	public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
		//log.info("Location changed: Moved from " + prev.getNameWithLowercaseType() + " to " + curr.getNameWithLowercaseType());
		FullSpectrumSweepAbility ability = GetAbility();
		ability.RescanSystem(curr);
		if (ability.hasScannedCurSystem && !ability.systemComplete) {
			boolean showReminder = true;
			if (Global.getSettings().getModManager().isModEnabled("lunalib"))
				showReminder = LunaSettings.getBoolean("ilysen_FullSpectrumSweep", "EnableReminderPings");
			if (showReminder)
				ability.GenerateMessage(curr);
		}
	}

	@Override
	public void reportEntityDiscovered(SectorEntityToken entity) {
		//log.info("Entity discovered: " + entity.getFullName());
		LocationAPI entityLoc = entity.getContainingLocation();
		if (!entity.isInHyperspace() && Global.getSector().getPlayerFleet().getContainingLocation() == entityLoc) {
			FullSpectrumSweepAbility ability = GetAbility();
			boolean systemWasComplete = ability.systemComplete;
			ability.RescanSystem(entityLoc);
			if (ability.systemComplete && !systemWasComplete && LunaSettings.getBoolean("ilysen_FullSpectrumSweep", "AlertOnComplete")) {
				String title = FullSpectrumSweepAbility.FRONT_END_TEXT + ": All signatures discovered";

				MessageIntel intel = new MessageIntel(title, Misc.getBasePlayerColor());
				intel.setIcon(ability.getSpriteName());

				Global.getSector().getCampaignUI().addMessage(intel);
			}
		}
	}

	private FullSpectrumSweepAbility GetAbility() {
		return (FullSpectrumSweepAbility) Global.getSector().getPlayerFleet().getAbility("ilysen_FullSpectrumSweep_FSSAbility");
	}
}
