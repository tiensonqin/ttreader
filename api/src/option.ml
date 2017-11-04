let map t f =
  match t with
  | None -> None
  | Some a -> Some (f a)

let bind t f =
  match t with
  | None -> None
  | Some x -> f x

let both x y =
  match x,y with
  | Some a, Some b -> Some (a,b)
  | _ -> None

let return x = Some x

module Let_syntax = struct
  let return = return
  let bind = bind
  let map = map
  let both = both

  module Open_in_body = struct
    let return = return
  end

  module Open_on_rhs = struct
    let return = return
  end
end
