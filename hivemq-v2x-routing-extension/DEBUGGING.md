# Debugging the HiveMQ V2X Extension in VS Code

This guide explains how to debug the HiveMQ extension using VS Code's Java debugger.

## Prerequisites

1. VS Code with the [Java Extension Pack](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
2. Docker and Docker Compose
3. The extension must be built before debugging

## Setup

The debugging configuration uses the Java Debug Wire Protocol (JDWP) to attach to the HiveMQ JVM running in Docker.

### Configuration Files

- **`.vscode/launch.json`**: Contains debug configurations
- **`.vscode/tasks.json`**: Contains build and Docker Compose tasks
- **`docker-compose.yml`**: Exposes debug port 5005 and sets `HIVEMQ_OPTS` for debugging

## Debugging Methods

### Method 1: Attach to Running HiveMQ (Recommended)

1. **Build the extension** (if not already built):
   ```bash
   cd hivemq-v2x-routing-extension
   ./gradlew clean buildExtension
   ```

2. **Start HiveMQ with debugging enabled**:
   ```bash
   docker-compose --profile hivemq up -d
   ```

3. **Wait for HiveMQ to start** (check logs):
   ```bash
   docker-compose logs -f hivemq
   ```
   Look for: `Listening for transport dt_socket at address: 5005`

4. **In VS Code**:
   - Open the Run and Debug panel (Ctrl+Shift+D / Cmd+Shift+D)
   - Select **"Debug HiveMQ Extension (Attach)"** from the dropdown
   - Press F5 or click the green play button
   - Set breakpoints in your extension code (e.g., in `V2XPublishInterceptor.java`)

5. **Test the extension**:
   - Use the test script: `python3 resources/hivemq-test-scripts/test_hivemq_extension.py`
   - Or use an MQTT client to publish to `/v2x/1/ingress/bsm`
   - Your breakpoints should trigger!

### Method 2: Build and Attach (One-Step)

1. **In VS Code**:
   - Select **"Debug HiveMQ Extension (Build & Attach)"** from the dropdown
   - Press F5
   - This will:
     - Build the extension
     - Start HiveMQ with debugging
     - Attach the debugger

2. **Set breakpoints** and test as above

## Debug Port Configuration

The debug port is configured in `docker-compose.yml`:
- **Port**: 5005 (default, configurable via `HIVEMQ_DEBUG_PORT` environment variable)
- **JVM Options**: Set via `HIVEMQ_OPTS` environment variable

The JVM debug options are:
```
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

- `server=y`: HiveMQ acts as the debug server
- `suspend=n`: Don't suspend on startup (set to `y` to wait for debugger)
- `address=*:5005`: Listen on all interfaces, port 5005

## Setting Breakpoints

Set breakpoints in your extension code:

1. **Publish Interceptor** (`V2XPublishInterceptor.java`):
   - Line 46: Topic check
   - Line 63: Location extraction
   - Line 74: Geohash routing

2. **Subscription Interceptor** (`V2XSubscriptionInterceptor.java`):
   - Line 43: Egress topic check
   - Line 57: Topic filter modification

3. **Message Decoder** (`MessageDecoderService.java`):
   - Line 63: BSM location extraction
   - Line 89: Codec initialization

4. **Geohash Routing** (`GeohashRoutingService.java`):
   - Line 45: Geohash computation
   - Line 74: Topic publishing

## Troubleshooting

### Debugger Won't Attach

1. **Check if HiveMQ is running**:
   ```bash
   docker-compose ps
   ```

2. **Check if debug port is listening**:
   ```bash
   docker-compose logs hivemq | grep "dt_socket"
   ```
   Should show: `Listening for transport dt_socket at address: 5005`

3. **Check port mapping**:
   ```bash
   docker-compose port hivemq 5005
   ```
   Should show: `0.0.0.0:5005`

4. **Verify extension is loaded**:
   ```bash
   docker-compose logs hivemq | grep "v2x-geohash-routing"
   ```

### Breakpoints Not Hitting

1. **Ensure source code matches compiled code**:
   - Rebuild the extension: `./gradlew clean buildExtension`
   - Rebuild Docker image: `docker-compose --profile hivemq build`

2. **Check if code is actually executing**:
   - Add log statements to verify execution path
   - Check HiveMQ logs: `docker-compose logs -f hivemq`

3. **Verify breakpoints are in active code paths**:
   - Ensure the code path is actually being executed
   - Check topic filters match your test messages

### Extension Not Loading

1. **Check extension installation**:
   ```bash
   docker-compose exec hivemq ls -la /opt/hivemq/extensions/v2x-geohash-routing/
   ```

2. **Check extension logs**:
   ```bash
   docker-compose logs hivemq | grep -i "extension\|error"
   ```

3. **Verify extension.xml**:
   ```bash
   docker-compose exec hivemq cat /opt/hivemq/extensions/v2x-geohash-routing/extension/extension.xml
   ```

## Using Testcontainers (Alternative)

For unit/integration testing with debugging, you can use HiveMQ Testcontainers as described in the [HiveMQ documentation](https://docs.hivemq.com/hivemq/latest/extensions/testing-extension.html):

```java
@Container
public final HiveMQContainer container = new HiveMQContainer(
    DockerImageName.parse("hivemq/hivemq-ce:latest"))
    .withExtension(MountableFile.forClasspathResource("v2x-geohash-routing"))
    .withDebugging(); // Enables debugging
```

Then attach VS Code debugger to the Testcontainer's debug port.

## Tips

1. **Use conditional breakpoints**: Right-click a breakpoint to add conditions
2. **Inspect variables**: Hover over variables or use the Variables panel
3. **Evaluate expressions**: Use the Debug Console to evaluate Java expressions
4. **Step through code**: Use F10 (step over), F11 (step into), Shift+F11 (step out)
5. **Watch expressions**: Add variables to the Watch panel for continuous monitoring

## References

- [HiveMQ Extension Testing Documentation](https://docs.hivemq.com/hivemq/latest/extensions/testing-extension.html)
- [VS Code Java Debugging](https://code.visualstudio.com/docs/java/java-debugging)
- [Java Debug Wire Protocol (JDWP)](https://docs.oracle.com/javase/8/docs/technotes/guides/jpda/jdwp-spec.html)


