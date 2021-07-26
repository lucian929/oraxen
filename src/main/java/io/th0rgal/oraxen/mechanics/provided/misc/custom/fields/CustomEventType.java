package io.th0rgal.oraxen.mechanics.provided.misc.custom.fields;

import io.th0rgal.oraxen.mechanics.provided.misc.custom.listeners.ClickListener;
import io.th0rgal.oraxen.mechanics.provided.misc.custom.listeners.CustomListener;

import java.util.List;

public enum CustomEventType {


    CLICK(ClickListener::new);

    public final CustomListenerConstructor constructor;

    CustomEventType(CustomListenerConstructor constructor) {
        this.constructor = constructor;
    }

    @FunctionalInterface
    interface CustomListenerConstructor {
        CustomListener create(String itemID, CustomEvent event,
                              List<CustomCondition> conditions, List<CustomAction> actions);
    }

}