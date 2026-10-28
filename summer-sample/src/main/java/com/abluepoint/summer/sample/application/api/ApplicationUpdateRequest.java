package com.abluepoint.summer.sample.application.api;

import java.util.List;
import com.abluepoint.summer.sample.application.application.*;

record ApplicationUpdateRequest(String name, String description) {
    ApplicationUpdateCommand command() { return new ApplicationUpdateCommand(name, description); }
}
