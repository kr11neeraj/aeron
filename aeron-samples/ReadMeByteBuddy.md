To Generate aeron-samples-byte-buddy-agent-1.51.0-SNAPSHOT.jar


PS C:\Users\kr28n\home\dev\git\aeron> .\gradlew.bat :aeron-samples:byteBuddySampleAgentJar

BUILD SUCCESSFUL in 5s
15 actionable tasks: 1 executed, 14 up-to-date


To Run the InterceptedBasicPublisher Program with Byte Buddy Agent 


S C:\Users\kr28n> cd C:\Users\kr28n\home\dev\git\aeron\aeron-samples\scripts
PS C:\Users\kr28n\home\dev\git\aeron\aeron-samples\scripts> .\byte-buddy-basic-publisher.cmd
[agent] installing agent for class=io.aeron.samples.bytebuddy.InterceptedBasicPublisher, method=publishMessage
[agent] transformed io.aeron.samples.bytebuddy.InterceptedBasicPublisher
Running Byte Buddy Aeron sample on aeron:udp?endpoint=localhost:20121 stream 1001
[agent] enter io.aeron.samples.bytebuddy.InterceptedBasicPublisher.publishMessage args=[0]
[agent] exit io.aeron.samples.bytebuddy.InterceptedBasicPublisher.publishMessage result=64 elapsedNs=149300
Application saw offer result 64
Subscriber received: Hello from Byte Buddy message 0
Application saw offer result 128
[agent] enter io.aeron.samples.bytebuddy.InterceptedBasicPublisher.publishMessage args=[1]
[agent] exit io.aeron.samples.bytebuddy.InterceptedBasicPublisher.publishMessage result=128 elapsedNs=51100
Subscriber received: Hello from Byte Buddy message 1
[agent] enter io.aeron.samples.bytebuddy.InterceptedBasicPublisher.publishMessage args=[2]
Application saw offer result 192
[agent] exit io.aeron.samples.bytebuddy.InterceptedBasicPublisher.publishMessage result=192 elapsedNs=31200
Subscriber received: Hello from Byte Buddy message 2
[agent] enter io.aeron.samples.bytebuddy.InterceptedBasicPublisher.publishMessage args=[3]
[agent] exit io.aeron.samples.bytebuddy.InterceptedBasicPublisher.publishMessage result=256 elapsedNs=41900
Application saw offer result 256
Subscriber received: Hello from Byte Buddy message 3
Application saw offer result 320
[agent] enter io.aeron.samples.bytebuddy.InterceptedBasicPublisher.publishMessage args=[4]
[agent] exit io.aeron.samples.bytebuddy.InterceptedBasicPublisher.publishMessage result=320 elapsedNs=28800
Subscriber received: Hello from Byte Buddy message 4



