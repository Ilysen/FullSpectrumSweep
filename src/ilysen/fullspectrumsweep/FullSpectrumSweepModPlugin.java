package ilysen.fullspectrumsweep;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;

import ilysen.fullspectrumsweep.campaign.abilities.FullSpectrumSweepAbility;

public class FullSpectrumSweepModPlugin extends BaseModPlugin {
	private static final Logger log = Global.getLogger(FullSpectrumSweepModPlugin.class);

	@Override
	public void onGameLoad(boolean newGame) {
		//log.info("Loading mod plugin...");
		Global.getSector().getListenerManager().addListener(new FullSpectrumSweepListener(), true);
		//log.info("Loading complete");
		if (!Global.getSector().getPlayerFleet().hasAbility("ilysen_FullSpectrumSweep_FSSAbility")) {
			Global.getSector().getCampaignUI().addMessage("Ability learned: %s", Misc.getTextColor(), FullSpectrumSweepAbility.FRONT_END_TEXT, "", Misc.getHighlightColor(), Color.black);
			Global.getSector().getCharacterData().addAbility("ilysen_FullSpectrumSweep_FSSAbility");
		}
	}
}
