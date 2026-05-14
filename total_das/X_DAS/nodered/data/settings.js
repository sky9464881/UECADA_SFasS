module.exports = {
  flowFile: "flows.json",
  credentialSecret: process.env.NODE_RED_CREDENTIAL_SECRET || "change-this-secret",
  uiPort: process.env.PORT || 1880,

  adminAuth: process.env.NODE_RED_USERNAME && process.env.NODE_RED_PASSWORD_HASH
    ? {
        type: "credentials",
        users: [
          {
            username: process.env.NODE_RED_USERNAME,
            password: process.env.NODE_RED_PASSWORD_HASH,
            permissions: "*"
          }
        ]
      }
    : undefined,

  contextStorage: {
    default: {
      module: "memory"
    },
    file: {
      module: "localfilesystem"
    }
  },

  logging: {
    console: {
      level: process.env.NODE_RED_LOG_LEVEL || "info",
      metrics: false,
      audit: false
    }
  },

  externalModules: {
    autoInstall: false
  },

  editorTheme: {
    projects: {
      enabled: false
    }
  },

  functionGlobalContext: {}
};

