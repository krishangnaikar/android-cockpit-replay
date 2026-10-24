# Cockpit replay

Replay a timestamped stream of simulated vehicle signals. Give the dashboard a virtual clock so a developer can pause or seek to the exact moment a reading goes stale.

## First acceptance test

Load a trace with a battery sample at 0 s and a recovery sample at 10 s. With a 5 s freshness limit, seeking directly to 7 s should show stale data. Seeking to 10 s should show the recovered reading. Rewinding and replaying must reproduce both states.