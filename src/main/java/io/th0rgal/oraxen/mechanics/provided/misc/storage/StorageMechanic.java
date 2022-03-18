package io.th0rgal.oraxen.mechanics.provided.misc.storage;

import io.th0rgal.oraxen.mechanics.Mechanic;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;


public class StorageMechanic extends Mechanic {
    private final int rows;
    private final String title;
    private final Sound sound;

    public StorageMechanic(MechanicFactory mechanicFactory, ConfigurationSection section) {
        super(mechanicFactory, section);
        this.rows = section.getInt("rows");
        this.title = section.getString("title");
        if(section.isConfigurationSection("open_sound")) {
            ConfigurationSection sound = section.getConfigurationSection("open_sound");
            this.sound = Sound.valueOf(sound.getString("sound"));
        } else {
            this.sound = null;
        }
    }

    public int getRows() {
        return rows;
    }

    public String getTitle() {
        return title;
    }

    public boolean hasSound(){
        return sound != null;
    }

    public Sound getSound() {
        return sound;
    }
}