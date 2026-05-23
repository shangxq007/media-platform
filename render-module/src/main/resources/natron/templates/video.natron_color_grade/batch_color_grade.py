# Natron batch script — video.natron_color_grade POC
# Invoked as: NatronRenderer -b -i MyReader <input> -w MyWriter <output> <this_script.py>
import os
import NatronEngine

def main():
    input_path = "__INPUT__"
    output_path = "__OUTPUT__"
    saturation = float(os.environ.get("NATRON_SATURATION", "1.15"))
    # POC: pass-through hook; real graphs would wire Grade node here.
    _ = (input_path, output_path, saturation)

if __name__ == "__main__":
    main()
