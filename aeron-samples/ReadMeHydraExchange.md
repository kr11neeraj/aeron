# Aeron Hydra-Style Exchange Sample

This sample is inspired by the Aeron/Hydra exchange architecture discussed in the EDX Markets talk:

https://www.youtube.com/watch?v=2MmjPXucIpw&t=1044s

Hydra is a proprietary Adaptive platform, so this sample does not depend on Hydra libraries. Instead it shows the same project shape with open-source Aeron pieces:

- Aeron Cluster replicates and sequences commands.
- `HydraExchangeCodec` acts like a generated binary codec/proxy layer.
- `HydraOrderBook` is deterministic service state.
- `HydraExchangeClusteredService` applies commands and snapshots state.
- `HydraExchangeClient` connects to the cluster and sends order flow.

## Build

From the Aeron repository root:

```powershell
.\gradlew.bat :aeron-all:jar
```

## Run On Windows

Open three terminals from `aeron-samples\scripts\cluster`:

```powershell
.\hydra-exchange-node.cmd 0
.\hydra-exchange-node.cmd 1
.\hydra-exchange-node.cmd 2
```

Then open a fourth terminal from the same directory:

```powershell
.\hydra-exchange-client.cmd 42 12
```

The client prints each command response with best bid and best ask. The service logs each deterministic state transition.

## Run On macOS Or Linux

From `aeron-samples/scripts/cluster`:

```bash
./hydra-exchange-cluster
./hydra-exchange-client 42 12
```

## What To Notice

- Inputs are compact binary commands, not Java objects crossing the cluster boundary.
- The service mutates state only from sequenced cluster messages.
- Snapshotting writes a deterministic order-id-sorted state image.
- Restarted nodes rebuild from snapshot plus Aeron Cluster log replay.
- The egress response carries enough data for clients to reconcile accepted and rejected commands.

This is intentionally small, but it gives you the bones of a Hydra-style trading service without needing proprietary generated code.
