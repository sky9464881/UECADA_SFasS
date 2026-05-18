FROM nodered/node-red:latest

RUN npm install --no-update-notifier --no-fund node-red-contrib-opcua@0.2.351

COPY patch-opcua-server.js /tmp/patch-opcua-server.js
RUN node /tmp/patch-opcua-server.js
