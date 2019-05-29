sublists :: [a] -> [[a]]
sublists [] = [[]]
sublists (e:es) = add e rest ++ rest
    where rest = sublists es

add :: a->[[a]] -> [[a]]
add h [] = []
add h (t:ts) = (h:t):add h ts



data Expr
    = Var Variable
    | Num Integer
    | Plus Expr Expr
    | Mins Expr Expr
    | Times Expr Expr
    | Div Expr Expr

data Variable = A | B

eval :: Integer -> Integer -> Expr -> Integer
eval _ _ (Num a) = a
eval aa bb (Plus a b) = eval aa bb a + eval aa bb b
eval aa bb (Mins a b) = eval aa bb a - eval aa bb b
eval aa bb (Times a b) = (eval aa bb a) * (eval aa bb b)
eval aa bb (Div a b) = (eval aa bb a) `div` (eval aa bb b)
eval aa _ (Var A) = aa
eval _ bb (Var B) = bb

root a b c =
    let d = sqrt(b*b - 4 * a * c)
    in [-(b + d) / (2*a), -(b - d) / (2*a)]

merge :: (Ord a) => [a] -> [a] -> [a]
merge a [] = a
merge [] b = b
merge (x:xs) (y:ys)
    | x > y     = y:merge (x:xs) ys
    | otherwise = x:merge xs (y:ys)

quickSort :: Ord a => [a] -> [a]
quickSort [] = []
quickSort (x:xs) =
    quickSort smallList ++ [x] ++ quickSort largeList
    where smallList = filter (<x) xs
          largeList = filter (>=x) xs

data Tree k v = Leaf | Node k v (Tree k v) (Tree k v)
                deriving (Eq, Show)

sameShape :: Tree k v -> Tree k v -> Bool
sameShape Leaf Leaf = True
sameShape Leaf _ = False
sameShape _ Leaf = False
sameShape (Node _ _ aln arn) (Node _ _ bln brn) =
    sameShape aln bln && sameShape arn brn

treeMapVal _ Leaf = Leaf
treeMapVal f (Node k v l r) = (Node k (f v) (treeMapVal f l) (treeMapVal f r))

maybeApply :: (a -> b) -> Maybe a -> Maybe b
maybeApply f a =
    case a of
        Just a -> Just (f a)
        Nothing -> Nothing

zWith :: (a -> b -> c) -> [a] -> [b] -> [c]
zWith f [] _ = []
zWith f _ [] = []
zWith f (x:xs) (y:ys) =
    f x y : zWith f xs ys

linearEqn :: Num a => a -> a -> [a] -> [a]
linearEqn m n = map (\x -> m*x + n)

sqrtPM :: (Floating a, Ord a) => a -> [a]
sqrtPM x
    | x > 0 = let y = sqrt x in [y, -y]
    | x == 0 = [0]
    | otherwise = []

allSqrts :: (Floating a, Ord a) => [a] -> [a]

allSqrts xs = foldl (++) [] (map sqrtPM xs)

func list@(x:xs) = map (sqrt) $ filter (>0) list
func' [] = []
func' (x:xs)
    | x > 0 = sqrt x : func' xs
    | otherwise = func' xs