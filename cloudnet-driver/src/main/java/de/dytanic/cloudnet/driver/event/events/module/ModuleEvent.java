package de.dytanic.cloudnet.driver.event.events.module;

import de.dytanic.cloudnet.driver.event.events.DriverEvent;
import de.dytanic.cloudnet.driver.module.IModuleProvider;
import de.dytanic.cloudnet.driver.module.IModuleWrapper;

public abstract class ModuleEvent extends DriverEvent {

    private IModuleProvider moduleProvider;

    private IModuleWrapper module;

    public ModuleEvent(IModuleProvider moduleProvider, IModuleWrapper module) {
        this.moduleProvider = moduleProvider;
        this.module = module;
    }

    public IModuleProvider getModuleProvider() {
        return this.moduleProvider;
    }

    public IModuleWrapper getModule() {
        return this.module;
    }
}