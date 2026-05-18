package com.example.phm.sensor.opcua;

import java.util.List;

public record XDasOpcUaNodeMapping(
        String nodeId,
        List<String> bufferKeys
) {
}
