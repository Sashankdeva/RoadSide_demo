# RoadSide

> A roadside mechanic in your phone that listens to your vehicle, looks at the problem, and tells you what to do — fully on-device.

RoadSide is an offline, phone-first vehicle assistance app that uses the microphone and camera already available on a smartphone to help users understand possible vehicle problems and decide what to do next.

Instead of requiring a diagnostic dongle, cloud service, or internet connection, RoadSide performs its sensing and analysis directly on the device.

## What RoadSide Does

RoadSide follows a simple flow:

**Describe → Listen → Inspect → Assessment → Solution**

1. **Listen**  
   Record a short vehicle sound using the phone microphone.

2. **Inspect**  
   Point the phone camera at the relevant vehicle component.

3. **Assessment**  
   RoadSide combines audio and visual evidence using an on-device rules engine.

4. **Solution**  
   The app explains:
   - What it observed
   - What it may mean
   - What the user can do
   - Possible tools needed
   - Safety guidance

5. **Ask RoadSide**  
   Users can ask contextual follow-up questions based on the current assessment.

## Current Prototype

The first end-to-end implementation focuses on **motorcycle chain and drivetrain checks**.

The current prototype can work with:

- Chain/drivetrain-related acoustic evidence
- Drive-chain detection
- Visible chain dirt/soiling
- Possible visible sprocket wear
- Evidence-based assessment
- Contextual maintenance guidance

The system is designed to remain conservative: when the evidence is unclear, RoadSide can return an uncertain result instead of pretending to know the answer.

## On-Device AI

RoadSide is designed around lightweight local inference.

### Audio

- YAMNet
- Custom trained drivetrain-noise head
- Local audio preprocessing and classification

### Vision

- MobileNetV3
- Custom trained visual condition heads
- Local image preprocessing and classification

### Decision Layer

A local rules engine combines the evidence from audio and vision and converts it into a user-facing assessment and solution.

## Technology Stack

- **Android**
- **Kotlin**
- **Jetpack Compose**
- **CameraX**
- **LiteRT**
- **YAMNet**
- **MobileNetV3**
- On-device rules engine

## Offline by Design

RoadSide does not require a cloud backend for its core analysis.

- No account required
- No cloud inference
- No internet connection required
- Works in airplane mode
- Audio recordings and photos stay on the device
- No internet permission in the application

This makes the approach useful for roadside situations where mobile connectivity may be unavailable.

## Architecture

```text
                ROADSide
                    │
             User interaction
                    │
          ┌─────────┴─────────┐
          │                   │
       Microphone           Camera
          │                   │
      Audio Model        Vision Model
          │                   │
          └─────────┬─────────┘
                    │
             Evidence Fusion
                    │
              Rules Engine
                    │
          ┌─────────┴─────────┐
          │                   │
      Assessment          Solution
                              │
                       Ask RoadSide
