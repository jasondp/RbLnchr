package net.minecraft.launcher.ui.bottombar;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.minecraft.launcher.Launcher;
import net.minecraft.launcher.authentication.AuthenticationService;
import net.minecraft.launcher.events.RefreshedProfilesListener;
import net.minecraft.launcher.events.RefreshedVersionsListener;
import net.minecraft.launcher.profile.Profile;
import net.minecraft.launcher.profile.ProfileManager;
import net.minecraft.launcher.updater.VersionManager;

public class PlayButtonPanel extends JPanel implements RefreshedProfilesListener, RefreshedVersionsListener {

   private final Launcher launcher;
   private final JButton playButton = new JButton("Play");


   public PlayButtonPanel(Launcher launcher) {
      this.launcher = launcher;
      launcher.getProfileManager().addRefreshedProfilesListener(this);
      this.checkState();
      this.createInterface();
      this.playButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            PlayButtonPanel.this.getLauncher().getVersionManager().getExecutorService().submit(new Runnable() {
               public void run() {
                  PlayButtonPanel.this.getLauncher().getGameLauncher().playGame();
               }
            });
         }
      });
   }

   protected void createInterface() {
      this.setLayout(new GridBagLayout());
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.fill = 1;
      constraints.weightx = 1.0D;
      constraints.weighty = 1.0D;
      constraints.gridy = 0;
      constraints.gridx = 0;
      this.add(this.playButton, constraints);
      this.playButton.setFont(this.playButton.getFont().deriveFont(1, (float)(this.playButton.getFont().getSize() + 2)));
   }

   public void onProfilesRefreshed(ProfileManager manager) {
      this.checkState();
   }

   public void checkState() {
      Profile profile = this.launcher.getProfileManager().getProfiles().isEmpty()?null:this.launcher.getProfileManager().getSelectedProfile();
      AuthenticationService auth = profile == null?null:this.launcher.getProfileManager().getAuthDatabase().getByUUID(profile.getPlayerUUID());
      if(auth != null && auth.isLoggedIn() && !this.launcher.getVersionManager().getVersions(profile.getVersionFilter()).isEmpty()) {
         if(auth.getSelectedProfile() == null) {
            this.playButton.setEnabled(true);
            this.playButton.setText("Play Demo");
         } else if(auth.canPlayOnline()) {
            this.playButton.setEnabled(true);
            this.playButton.setText("Play");
         } else {
            this.playButton.setEnabled(true);
            this.playButton.setText("Play Offline");
         }
      } else {
         this.playButton.setEnabled(false);
         this.playButton.setText("Play");
      }

      if(this.launcher.getGameLauncher().isWorking()) {
         this.playButton.setEnabled(false);
      }

   }

   public void onVersionsRefreshed(VersionManager manager) {
      this.checkState();
   }

   public boolean shouldReceiveEventsInUIThread() {
      return true;
   }

   public Launcher getLauncher() {
      return this.launcher;
   }
}
