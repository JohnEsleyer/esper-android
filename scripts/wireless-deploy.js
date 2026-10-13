#!/usr/bin/env bun

import { execSync, spawnSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import readline from 'readline';
import os from 'os';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Paths
const ROOT_DIR = path.resolve(__dirname, '..');
const APK_PATH = path.join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
const CONFIG_FILE = path.join(ROOT_DIR, '.wireless-device.json');
const PACKAGE_NAME = 'com.johnesleyer.esper';
const ACTIVITY_NAME = 'com.esper.android.MainActivity';

// Color formatting
const c = {
  reset: '\x1b[0m',
  bright: '\x1b[1m',
  dim: '\x1b[2m',
  green: '\x1b[32m',
  blue: '\x1b[34m',
  cyan: '\x1b[36m',
  yellow: '\x1b[33m',
  red: '\x1b[31m',
  magenta: '\x1b[35m',
};

const args = process.argv.slice(2);
const skipBuild = args.includes('--skip-build') || args.includes('-s');
const forcePair = args.includes('--pair') || args.includes('-p');

// Helper for interactive prompts
function prompt(question) {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });
  return new Promise((resolve) => {
    rl.question(question, (answer) => {
      rl.close();
      resolve(answer.trim());
    });
  });
}

// Find ADB binary
function getAdbPath() {
  try {
    execSync('adb version', { stdio: 'ignore' });
    return 'adb';
  } catch {
    const sdkDirs = [
      process.env.ANDROID_HOME,
      process.env.ANDROID_SDK_ROOT,
      path.join(os.homedir(), 'Android', 'Sdk'),
      path.join(os.homedir(), 'AppData', 'Local', 'Android', 'Sdk'),
      path.join(os.homedir(), 'Library', 'Android', 'sdk'),
    ].filter(Boolean);

    for (const sdk of sdkDirs) {
      const candidate = path.join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
      if (fs.existsSync(candidate)) return `"${candidate}"`;
    }
  }
  return null;
}

// Remembered config
function loadConfig() {
  try {
    if (fs.existsSync(CONFIG_FILE)) {
      return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
    }
  } catch {}
  return {};
}

function saveConfig(data) {
  try {
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(data, null, 2));
  } catch {}
}

async function main() {
  console.log(`\n${c.cyan}======================================================${c.reset}`);
  console.log(`${c.bright}${c.blue}       Esper Android — Wireless Deploy Engine        ${c.reset}`);
  console.log(`${c.cyan}======================================================${c.reset}\n`);

  const adb = getAdbPath();
  if (!adb) {
    console.error(`${c.red}✖ Error: 'adb' was not found on your system PATH.${c.reset}`);
    console.log(`Please install Android SDK platform-tools or set ANDROID_HOME.`);
    process.exit(1);
  }

  // 1. Compile APK (unless skipped)
  if (!skipBuild) {
    console.log(`${c.yellow}⚙ [1/4] Compiling Debug APK via Gradle...${c.reset}`);
    const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
    const buildResult = spawnSync(gradlew, ['assembleDebug', '--no-daemon'], {
      cwd: ROOT_DIR,
      stdio: 'inherit',
      shell: true,
    });

    if (buildResult.status !== 0) {
      console.error(`\n${c.red}✖ Gradle build failed. Check the errors above.${c.reset}`);
      process.exit(1);
    }

    if (!fs.existsSync(APK_PATH)) {
      console.error(`\n${c.red}✖ APK not found at ${APK_PATH}${c.reset}`);
      process.exit(1);
    }
    console.log(`${c.green}✔ Build succeeded!${c.reset}\n`);
  } else {
    console.log(`${c.yellow}⚡ [1/4] Skipping build step (using existing APK).${c.reset}`);
    if (!fs.existsSync(APK_PATH)) {
      console.error(`${c.red}✖ No APK found at ${APK_PATH}. Run build first!${c.reset}`);
      process.exit(1);
    }
    console.log(`${c.dim}Using: ${APK_PATH}${c.reset}\n`);
  }

  // 2. Discover devices
  console.log(`${c.yellow}🔍 [2/4] Detecting devices on local network...${c.reset}`);

  // Query already-connected ADB devices
  let connectedDevices = [];
  try {
    const out = execSync(`${adb} devices`, { encoding: 'utf-8' });
    connectedDevices = out
      .split('\n')
      .slice(1)
      .map((line) => line.trim())
      .filter((line) => line && line.includes('\tdevice'))
      .map((line) => line.split('\t')[0]);
  } catch {}

  // Query mDNS wireless services if supported
  let mdnsDevices = [];
  try {
    const mdnsOut = execSync(`${adb} mdns services`, { encoding: 'utf-8', timeout: 2500 });
    const matches = [...mdnsOut.matchAll(/([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+)/g)];
    mdnsDevices = matches.map((m) => m[1]);
  } catch {}

  const config = loadConfig();
  let targetDevice = null;

  if (connectedDevices.length > 0 && !forcePair) {
    console.log(`${c.green}Found active connected ADB device(s):${c.reset}`);
    connectedDevices.forEach((dev, idx) => {
      console.log(`  [${idx + 1}] ${c.bright}${dev}${c.reset}`);
    });
    console.log(`  [${connectedDevices.length + 1}] Pair / Connect a new device via Wireless Debugging`);

    const choice = await prompt(`\nSelect device (1-${connectedDevices.length + 1}) [default 1]: `);
    const chosenIdx = parseInt(choice || '1', 10) - 1;

    if (chosenIdx >= 0 && chosenIdx < connectedDevices.length) {
      targetDevice = connectedDevices[chosenIdx];
    }
  }

  // 3. Guide user through Android Wireless Debugging if not connected
  if (!targetDevice) {
    console.log(`\n${c.magenta}┌────────────────────────────────────────────────────────┐${c.reset}`);
    console.log(`${c.magenta}│${c.reset} ${c.bright}📱 GUIDE: Enable Wireless Debugging on your Android     ${c.reset}${c.magenta}│${c.reset}`);
    console.log(`${c.magenta}├────────────────────────────────────────────────────────┤${c.reset}`);
    console.log(`${c.magenta}│${c.reset} 1. On your phone: Open ${c.yellow}Settings → Developer options${c.reset}     ${c.magenta}│${c.reset}`);
    console.log(`${c.magenta}│${c.reset} 2. Scroll to ${c.yellow}'Wireless debugging'${c.reset} & toggle it ${c.green}ON${c.reset}        ${c.magenta}│${c.reset}`);
    console.log(`${c.magenta}│${c.reset} 3. Ensure phone and PC are on the ${c.yellow}SAME Wi-Fi network${c.reset}    ${c.magenta}│${c.reset}`);
    console.log(`${c.magenta}│${c.reset} 4. Tap ${c.yellow}'Wireless debugging'${c.reset} to open its sub-menu      ${c.magenta}│${c.reset}`);
    console.log(`${c.magenta}└────────────────────────────────────────────────────────┘${c.reset}\n`);

    if (mdnsDevices.length > 0) {
      console.log(`${c.cyan}mDNS auto-detected Wi-Fi target(s):${c.reset}`);
      mdnsDevices.forEach((ip, i) => console.log(`  [${i + 1}] ${ip}`));
    }

    const isPairedAnswer = await prompt(`Have you already paired this computer with your phone? (Y/n) [default Y]: `);
    const isPaired = isPairedAnswer.toLowerCase() !== 'n' && !forcePair;

    if (!isPaired) {
      console.log(`\n${c.bright}--- Pairing Step ---${c.reset}`);
      console.log(`On phone: Tap ${c.yellow}"Pair device with pairing code"${c.reset}`);
      console.log(`You will see an IP, Port, and a 6-digit Wi-Fi pairing code.`);

      const pairEndpoint = await prompt(`Enter Pairing IP & Port (e.g., 192.168.1.50:39485): `);
      const pairCode = await prompt(`Enter 6-digit Pairing Code: `);

      console.log(`\n${c.yellow}Executing: adb pair ${pairEndpoint} ${pairCode}${c.reset}`);
      const pairResult = spawnSync(adb, ['pair', pairEndpoint, pairCode], { stdio: 'inherit' });

      if (pairResult.status !== 0) {
        console.error(`${c.red}✖ Pairing failed. Please verify the IP, port, and code.${c.reset}`);
        process.exit(1);
      }
      console.log(`${c.green}✔ Device paired successfully!${c.reset}\n`);
    }

    // Connect step
    console.log(`\n${c.bright}--- Connect Step ---${c.reset}`);
    console.log(`Look at the ${c.yellow}main Wireless debugging screen${c.reset} (under "IP address & Port").`);
    
    let defaultEndpoint = config.lastEndpoint || (mdnsDevices[0] || '');
    const promptMsg = defaultEndpoint 
      ? `Enter IP address & Port [default ${defaultEndpoint}]: ` 
      : `Enter IP address & Port (e.g. 192.168.1.50:41235): `;

    const connectEndpoint = (await prompt(promptMsg)) || defaultEndpoint;

    if (!connectEndpoint) {
      console.error(`${c.red}✖ Error: No IP:Port specified.${c.reset}`);
      process.exit(1);
    }

    console.log(`\n${c.yellow}Connecting to ${connectEndpoint}...${c.reset}`);
    const connectOut = execSync(`${adb} connect ${connectEndpoint}`, { encoding: 'utf-8' });
    console.log(connectOut.trim());

    if (!connectOut.includes('connected')) {
      console.error(`${c.red}✖ Could not connect to ${connectEndpoint}. Check IP/Port on phone.${c.reset}`);
      process.exit(1);
    }

    targetDevice = connectEndpoint;
    saveConfig({ lastEndpoint: connectEndpoint });
  }

  // 4. Install APK
  console.log(`\n${c.yellow}🚀 [3/4] Installing APK onto ${c.bright}${targetDevice}${c.reset}${c.yellow}...${c.reset}`);
  const installResult = spawnSync(adb, ['-s', targetDevice, 'install', '-r', '-d', APK_PATH], {
    stdio: 'inherit',
  });

  if (installResult.status !== 0) {
    console.error(`\n${c.red}✖ Installation failed.${c.reset}`);
    process.exit(1);
  }
  console.log(`${c.green}✔ App installed successfully!${c.reset}`);

  // 5. Launch App
  console.log(`\n${c.yellow}📱 [4/4] Launching Esper on ${targetDevice}...${c.reset}`);
  execSync(`${adb} -s ${targetDevice} shell am start -n ${PACKAGE_NAME}/${ACTIVITY_NAME}`, {
    stdio: 'inherit',
  });

  console.log(`\n${c.green}======================================================${c.reset}`);
  console.log(`${c.bright}${c.green}✔ SUCCESS: Esper is now running on your phone!${c.reset}`);
  console.log(`${c.green}======================================================${c.reset}\n`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
