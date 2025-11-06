# CCAPI Usage Tracker

A Java application to monitor and track CCAPI (Claude Compatible API) usage across multiple accounts. Provides both a command-line interface and a JavaFX floating widget.

## Features

- **Multi-account support**: Track usage for multiple CCAPI accounts simultaneously
- **5-hour usage monitoring**: View utilization percentage and time elapsed
- **Usage-to-time ratio**: Understand if you're using resources proportionally to time
- **Command-line interface**: Simple CLI with colored output
- **JavaFX floating widget**: Compact, always-on-top desktop widget with progress bars
- **Auto-refresh**: Widget automatically updates every 60 seconds

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- Base CCAPI models project (`xyz-jphil-ccapis-base`)

## Installation

1. First, build and install the base project:
```bash
cd ../xyz-jphil-ccapis-base
mvn clean install
```

2. Build the usage tracker:
```bash
cd ../xyz-jphil-ccapis-usage_tracker
mvn clean package
```

## Configuration

### Generate Settings Template

Run the template generator to create an empty configuration file:

```bash
mvn exec:java -Dexec.mainClass="xyz.jphil.ccapis.usage_tracker.util.TemplateGenerator"
```

This creates `settings.xml` at: `<user.home>/xyz-jphil/ccapis/usage_tracker/settings.xml`

### Edit Settings

Open the generated `settings.xml` and fill in your details:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<settings>
    <ccapiBaseUrl>https://your-ccapi-url.com</ccapiBaseUrl>
    <credentials>
        <credential id="acc1" name="Work Account" email="work@example.com"
                    orgId="org-123456" key="sk-ant-..." active="true"/>
        <credential id="acc2" name="Personal Account" email="personal@example.com"
                    orgId="org-789012" key="sk-ant-..." active="true"/>
    </credentials>
</settings>
```

**Settings explained:**
- `ccapiBaseUrl`: Your CCAPI base URL (e.g., `https://api.anthropic.com`)
  - **Environment variable support**: Use `%VARIABLE_NAME%` syntax to reference environment variables
  - Example: `<ccapiBaseUrl>%CCAPI_BASE_URL%</ccapiBaseUrl>`
  - Example with fallback: `<ccapiBaseUrl>https://default-url.com</ccapiBaseUrl>` (static value)
- `id`: Short alias for the account (shown in displays)
- `name`: Friendly name
- `email`: Associated email address
- `orgId`: Organization ID from API
- `key`: Session key (format: `sk-ant-...`)
- `active`: Set to `true` to enable tracking, `false` to disable

### Using Environment Variables

You can use environment variables in `ccapiBaseUrl` to keep sensitive configuration out of the settings file:

**Windows:**
```cmd
set CCAPI_BASE_URL=https://api.anthropic.com
```

**Linux/Mac:**
```bash
export CCAPI_BASE_URL=https://api.anthropic.com
```

Then in `settings.xml`:
```xml
<ccapiBaseUrl>%CCAPI_BASE_URL%</ccapiBaseUrl>
```

If the environment variable is not set, the literal value (with `%` symbols) will be used as-is.

## Usage

### Command-Line Interface

Run the CLI to view current usage status:

```bash
mvn exec:java -Dexec.mainClass="xyz.jphil.ccapis.usage_tracker.cli.UsageTrackerCLI"
```

Or use the packaged JAR:

```bash
java -jar target/xyz-jphil-ccapis-usage_tracker-1.0.jar
```

**Example output:**
```
================================================================================
CCAPI Usage Tracker
================================================================================
acc1            | 5hr Usage:  26.0% | Time:  45.3% | Ratio: 0.57 | Reset in: 2h 44m
acc2            | 5hr Usage:  10.0% | Time:  45.3% | Ratio: 0.22 | Reset in: 2h 44m
--------------------------------------------------------------------------------
Average Usage: 18.0% | Average Ratio: 0.40
```

### JavaFX Floating Widget

Run the floating widget for continuous desktop monitoring:

```bash
mvn javafx:run
```

Or:

```bash
java -jar target/xyz-jphil-ccapis-usage_tracker-1.0.jar xyz.jphil.ccapis.usage_tracker.ui.UsageWidget
```

**Widget features:**
- Compact, transparent design
- Always on top
- Draggable to position anywhere on screen
- Auto-updates every 60 seconds
- Color-coded progress bars:
  - Green: < 60% usage
  - Orange: 60-80% usage
  - Red: > 80% usage

## Understanding the Metrics

### 5-Hour Usage
Percentage of your 5-hour usage quota consumed.

### Time Elapsed
Percentage of the 5-hour window that has passed since last reset.

### Usage-to-Time Ratio
Indicates how your usage compares to time elapsed:
- **< 1.0** (Green): Using less than proportional to time - good!
- **≈ 1.0** (Orange): Using proportionally to time - watch closely
- **> 1.2** (Red): Using more than proportional to time - slow down!

**Example:**
- If 50% of time has passed but you've used 25% of quota → Ratio = 0.5 (safe)
- If 50% of time has passed but you've used 75% of quota → Ratio = 1.5 (danger!)

## Project Structure

```
src/main/java/xyz/jphil/ccapis/usage_tracker/
├── api/
│   └── UsageApiClient.java       # HTTP client for fetching usage data
├── cli/
│   └── UsageTrackerCLI.java      # Command-line interface
├── config/
│   ├── Settings.java             # Settings data model
│   └── SettingsManager.java      # XML configuration manager
├── model/
│   └── AccountUsage.java         # Usage metrics calculator
├── ui/
│   └── UsageWidget.java          # JavaFX floating widget
└── util/
    └── TemplateGenerator.java    # Settings template generator
```

## Dependencies

- **Base models**: `xyz-jphil-ccapis-base` (Usage, UsageData POJOs)
- **HTTP client**: OkHttp 4.12.0
- **JSON**: Jackson 2.15.2
- **XML**: Jakarta XML Bind 4.0
- **CLI**: JLine 3.25.0
- **GUI**: JavaFX 23
- **Code generation**: Lombok 1.18.38

## Troubleshooting

### "Failed to load settings"
Ensure `settings.xml` exists and is properly formatted. Run the template generator again if needed.

### "No active credentials found"
Set `active="true"` for at least one credential in `settings.xml`.

### "Unexpected response code: 401"
Your session key may have expired. Update the `key` attribute in `settings.xml`.

### JavaFX not launching
Ensure JavaFX 23+ is installed and your Java installation supports JavaFX.

## License

Part of the xyz-jphil CCAPI client project.
