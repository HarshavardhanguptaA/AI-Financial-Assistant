const fs = require('fs');
const path = require('path');

// Read BACKEND_API_URL from environment
const backendUrl = process.env.BACKEND_API_URL ? process.env.BACKEND_API_URL.trim() : '';

const configContent = `/**
 * Financial Copilot — Frontend Runtime Configuration
 * Generated automatically during build / deployment
 */
window.__APP_CONFIG__ = window.__APP_CONFIG__ || {
    BACKEND_URL: ${JSON.stringify(backendUrl)}
};
`;

const targetPath = path.resolve(__dirname, '..', 'public', 'config.js');
fs.writeFileSync(targetPath, configContent, 'utf8');
console.log(`[Config] Injected BACKEND_API_URL: "${backendUrl || '(local relative)'}" into public/config.js`);
