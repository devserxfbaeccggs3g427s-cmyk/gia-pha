package com.familya.treeaccess.application.port.in;

import java.util.UUID;

public record CreateTreeCommand(UUID ownerUserId, String name) { }