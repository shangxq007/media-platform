(*
  FAOF-2 complementary bounded proof slice.

  Coq proves completeness and dependency precedence for the same three-node
  platform model used by the Lean POC. It does not model implementation
  mechanics and is not production authority.
*)

From Coq Require Import List Arith.
Import ListNotations.

Inductive node : Type := root | left | right.

Definition explicit_order : list node := [root; left; right].

Definition position (value : node) : nat :=
  match value with
  | root => 0
  | left => 1
  | right => 2
  end.

Inductive dependency : node -> node -> Prop :=
| root_before_left : dependency root left
| root_before_right : dependency root right.

Theorem explicit_order_complete :
  forall value, In value explicit_order.
Proof.
  intros value.
  destruct value; simpl; auto.
Qed.

Theorem dependency_respects_explicit_order :
  forall source target,
    dependency source target -> position source < position target.
Proof.
  intros source target edge.
  inversion edge; simpl; auto.
Qed.

Theorem explicit_order_nodup : NoDup explicit_order.
Proof.
  repeat constructor; simpl; intuition discriminate.
Qed.
