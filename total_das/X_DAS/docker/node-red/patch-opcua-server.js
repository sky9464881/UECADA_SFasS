const fs = require("fs");

const target = "/usr/src/node-red/node_modules/node-red-contrib-opcua/opcua/104-opcuaserver.js";
const marker = /timestamped_get:\s*\(\)\s*=>\s*\{\s*return newValue;\s*\},/;
const replacement = `timestamped_get: () => {
                                    let currentValue;
                                    if (valueRank >= 2) {
                                        currentValue = new opcua.Variant({
                                            arrayType,
                                            dimensions,
                                            dataType: opcuaDataType,
                                            value: variables[variableId]
                                        });
                                    }
                                    else {
                                        currentValue = new opcua.Variant({
                                            arrayType,
                                            dataType: opcuaDataType,
                                            value: variables[variableId]
                                        });
                                    }
                                    return new DataValue({
                                        serverPicoseconds: 0,
                                        serverTimestamp: new Date(),
                                        sourcePicoseconds: 0,
                                        sourceTimestamp: variablesTs[variableId] || new Date(),
                                        statusCode: variablesStatus[variableId] || opcua.StatusCodes.Good,
                                        value: currentValue
                                    });
                                },`;

let source = fs.readFileSync(target, "utf8");
if (!marker.test(source)) {
  if (source.includes("currentValue = new opcua.Variant")) {
    console.log("node-red-contrib-opcua server getter already patched");
    process.exit(0);
  }
  throw new Error("Could not find node-red-contrib-opcua server getter patch marker");
}

source = source.replace(marker, replacement);
fs.writeFileSync(target, source);
console.log("patched node-red-contrib-opcua server getter for live values");
