# RetroPack — Staging Directory

This directory is the designated clean-room quarantine area for cloning and downloading upstream reference repositories:

```text
staging/
├── ksupatcher/          # git clone https://github.com/AkuaTech/ksupatcher
├── ludere/              # git clone https://github.com/tytydraco/Ludere
├── retra/               # git clone https://github.com/prashantchataut/Retra
└── garnacha-boy/        # git clone https://github.com/TrebuchetDynamics/garnacha-boy-android
```

## Policy
1. Files here remain pristine upstream copies.
2. Do not edit files directly inside `staging/`.
3. Inspect and port relevant files into the actual project modules (`app/`, `core/`, `domain/`) as specified in [masterplan.md](file:///c:/Users/ok/Documents/retro%20manager/masterplan.md#L34-L73).
