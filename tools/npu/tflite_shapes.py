"""Read input/output tensor shapes straight out of a .tflite flatbuffer.

No TensorFlow on this laptop, and the model we have (handoff/npu/selfie_multiclass.tflite,
from HuggingFace) is not byte-identical to the one the Google sample downloads, so the
sample's hard-coded 256x256x6 is an assumption until something reads the actual file.

Hand-rolled minimal flatbuffer table walk against the TFLite schema field order:
  Model    : version(0) operator_codes(1) subgraphs(2) description(3) buffers(4)
             metadata_buffer(5) metadata(6) signature_defs(7)
  SubGraph : tensors(0) inputs(1) outputs(2) operators(3) name(4)
  Tensor   : shape(0) type(1) buffer(2) name(3) ...
"""

import struct
import sys

TENSOR_TYPES = {
    0: "FLOAT32", 1: "FLOAT16", 2: "INT32", 3: "UINT8", 4: "INT64",
    5: "STRING", 6: "BOOL", 7: "INT16", 8: "COMPLEX64", 9: "INT8",
    10: "FLOAT64", 11: "COMPLEX128", 12: "UINT64", 13: "RESOURCE",
    14: "VARIANT", 15: "UINT32", 16: "UINT16", 17: "INT4",
}


class Buf:
    def __init__(self, data):
        self.d = data

    def u32(self, p):
        return struct.unpack_from("<I", self.d, p)[0]

    def i32(self, p):
        return struct.unpack_from("<i", self.d, p)[0]

    def u16(self, p):
        return struct.unpack_from("<H", self.d, p)[0]

    def i8(self, p):
        return struct.unpack_from("<b", self.d, p)[0]

    def field(self, table, idx):
        """Absolute position of field `idx` in `table`, or None if absent."""
        vtable = table - self.i32(table)
        vsize = self.u16(vtable)
        off = 4 + idx * 2
        if off >= vsize:
            return None
        voff = self.u16(vtable + off)
        return None if voff == 0 else table + voff

    def indirect(self, p):
        """Follow a uoffset stored at p (tables and vectors are referenced this way)."""
        return p + self.u32(p)

    def vector(self, table, idx):
        """(start_of_elements, count) for the vector field `idx`, or (None, 0)."""
        p = self.field(table, idx)
        if p is None:
            return None, 0
        v = self.indirect(p)
        return v + 4, self.u32(v)

    def string(self, table, idx):
        p = self.field(table, idx)
        if p is None:
            return None
        v = self.indirect(p)
        return self.d[v + 4: v + 4 + self.u32(v)].decode("utf-8", "replace")


def main(path):
    with open(path, "rb") as fh:
        data = fh.read()
    buf = Buf(data)

    magic = data[4:8]
    print(f"file          : {path}")
    print(f"size          : {len(data):,} bytes")
    print(f"identifier    : {magic!r}")
    if magic != b"TFL3":
        print("!! not a TFL3 flatbuffer — stopping")
        return 1

    model = buf.indirect(0)

    desc = buf.string(model, 3)
    print(f"description   : {desc!r}")

    subgraphs, n_sub = buf.vector(model, 2)
    print(f"subgraphs     : {n_sub}")

    for s in range(n_sub):
        sg = buf.indirect(subgraphs + s * 4)
        name = buf.string(sg, 4)
        tensors, n_tensors = buf.vector(sg, 0)
        inputs, n_in = buf.vector(sg, 1)
        outputs, n_out = buf.vector(sg, 2)
        _, n_ops = buf.vector(sg, 3)
        print(f"\n--- subgraph {s} name={name!r} "
              f"tensors={n_tensors} operators={n_ops} ---")

        def describe(tensor_index, role):
            t = buf.indirect(tensors + tensor_index * 4)
            shape_at, n_dims = buf.vector(t, 0)
            shape = [buf.i32(shape_at + i * 4) for i in range(n_dims)] if shape_at else []
            type_at = buf.field(t, 1)
            ttype = TENSOR_TYPES.get(buf.i8(type_at) if type_at else 0, "?")
            tname = buf.string(t, 3)
            elems = 1
            for d in shape:
                elems *= d
            print(f"  {role:6s} [{tensor_index:4d}] {tname!r}")
            print(f"         shape={shape} type={ttype} elements={elems:,}")
            return shape, ttype

        for i in range(n_in):
            describe(buf.i32(inputs + i * 4), "INPUT")
        for i in range(n_out):
            describe(buf.i32(outputs + i * 4), "OUTPUT")

    # Signature defs carry the names CompiledModel.run(Map<String,...>) would use.
    sigs, n_sigs = buf.vector(model, 7)
    print(f"\nsignature_defs: {n_sigs}")
    for i in range(n_sigs):
        sd = buf.indirect(sigs + i * 4)
        # SignatureDef: inputs(0) outputs(1) signature_key(2) ...
        print(f"  key={buf.string(sd, 2)!r}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
