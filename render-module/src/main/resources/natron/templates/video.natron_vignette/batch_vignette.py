# Natron batch script — video.natron_vignette POC (Phase 2)
import os
# Invoked as: NatronRenderer -b -i MyReader <input> -w MyWriter <output> <this_script.py>
# Placeholders __INPUT__ and __OUTPUT__ are substituted when the script is materialized per job.

reader = app.createReader("__INPUT__")
writer = app.createWriter("__OUTPUT__")
reader.setScriptName("MyReader")
writer.setScriptName("MyWriter")

# Vignette via Grade node (gamma driven by intensity env when passed via -c)
grade = app.createNode("net.sf.openfx.GradePlugin")
if grade is not None:
    grade.setScriptName("MyGrade")
    grade.connectInput(0, reader)
    writer.connectInput(0, grade)
    try:
        intensity = float(os.environ.get("NATRON_INTENSITY", "0.5"))
        gamma = max(0.2, 1.0 - (intensity * 0.35))
        grade.getParam("gamma").set(gamma, 0)
        grade.getParam("gamma").set(gamma, 1)
        grade.getParam("gamma").set(gamma, 2)
    except Exception:
        writer.connectInput(0, reader)
else:
    writer.connectInput(0, reader)

format_type = writer.getParam("formatType")
if format_type is not None:
    format_type.setValue(0)
