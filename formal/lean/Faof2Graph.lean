/-
  FAOF-2 bounded proof model.

  This model is deliberately smaller than the Java implementation. It proves
  platform laws for a finite graph with one root and two independent children,
  using an explicit semantic-node order. It does not model Java or JGraphT
  mechanics and is not production authority.
-/

namespace Faof2

inductive Node where
  | root
  | left
  | right
  deriving DecidableEq, Repr

def explicitOrder : List Node := [.root, .left, .right]

def position : Node → Nat
  | .root => 0
  | .left => 1
  | .right => 2

def Dependency : Node → Node → Prop
  | .root, .left => True
  | .root, .right => True
  | _, _ => False

def IsTopologicalOrder (order : List Node) : Prop :=
  order.Nodup ∧
    (∀ node, node ∈ order) ∧
    (∀ source target, Dependency source target →
      order.idxOf source < order.idxOf target)

theorem explicit_order_complete (node : Node) : node ∈ explicitOrder := by
  cases node <;> simp [explicitOrder]

theorem explicit_order_nodup : explicitOrder.Nodup := by
  simp [explicitOrder]

theorem dependency_respects_explicit_order
    (source target : Node)
    (dependency : Dependency source target) :
    explicitOrder.idxOf source < explicitOrder.idxOf target := by
  cases source <;> cases target <;>
    simp [Dependency, explicitOrder] at dependency ⊢ <;> decide

theorem explicit_order_is_topological : IsTopologicalOrder explicitOrder := by
  refine ⟨explicit_order_nodup, explicit_order_complete, ?_⟩
  exact dependency_respects_explicit_order

def selectFirst (first second : Node) : Node :=
  if position first ≤ position second then first else second

theorem explicit_ready_order_is_deterministic :
    selectFirst .right .left = selectFirst .left .right := by
  rfl

end Faof2
