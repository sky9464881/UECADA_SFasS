FROM nodered/node-red:latest

RUN npm install --unsafe-perm --no-update-notifier --no-fund node-red-contrib-opcua@0.2.351

COPY docker/node-red/patch-opcua-server.js /tmp/patch-opcua-server.js
RUN node /tmp/patch-opcua-server.js

RUN node -e "const path = require('path'); const opcua = require('node-opcua'); const manager = new opcua.OPCUACertificateManager({ rootFolder: path.join(process.env.HOME || '/usr/src/node-red', '.config/node-red-opcua-nodejs/PKI'), automaticallyAcceptUnknownCertificate: true }); manager.initialize().then(() => console.log('OPC UA client PKI initialized')).catch((error) => { console.error(error); process.exit(1); });"
