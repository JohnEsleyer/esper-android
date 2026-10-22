Welcome to Rust! Rust is a systems programming language focused on safety, speed, and concurrency, all without needing a garbage collector. It achieves this through a unique system called ownership.
To get you up to speed efficiently, we'll break down the core fundamentals into digestible parts, complete with code examples.
1. Variables and Mutability (let and mut)
By default, variables in Rust are immutable (read-only). If you want to change a variable's value, you must explicitly mark it as mutable using mut.
fn main() {
    let x = 5; // Immutable
    // x = 6;  // Error! Cannot mutate immutable variable

    let mut y = 10; // Mutable
    y = 15;         // This works fine!
    
    println!("y is: {}", y);
}

2. Basic Data Types
Rust is a statically typed language, meaning it must know the types of all variables at compile time. Usually, the compiler can infer types, but sometimes you need to annotate them.
 * Scalars: Integers (i32, u64), Floating-point (f64), Booleans (bool), Characters (char).
 * Compound: Tuples and Arrays.
fn main() {
    let guess: u32 = "42".parse().expect("Not a number!"); // Explicit type annotation
    let pi: f64 = 3.14159;                               // 64-bit float
    let is_active: bool = true;                          // Boolean
    let letter: char = 'R';                              // Unicode scalar value
}

3. Control Flow (if and loops)
Conditional branching and looping are straightforward, but note that if can also be used as an expression (like a ternary operator).
fn main() {
    let number = 6;

    // If-else expression
    if number % 4 == 0 {
        println!("Number is divisible by 4");
    } else if number % 3 == 0 {
        println!("Number is divisible by 3");
    } else {
        println!("Number is not divisible by 4 or 3");
    }

    // Loops: loop, while, and for
    let mut count = 0;
    
    // Infinite loop with break
    loop {
        count += 1;
        if count == 3 {
            break;
        }
    }

    // For loop over a range (inclusive of 1, exclusive of 4)
    for i in 1..4 {
        println!("i is: {}", i);
    }
}

4. Ownership (Rust’s Superpower)
Ownership is how Rust manages memory safely without a garbage collector. Every value in Rust has a variable called its owner.
The core rules of ownership:
 * Each value in Rust has an owner.
 * There can only be one owner at a time.
 * When the owner goes out of scope, the value will be dropped.
fn main() {
    let s1 = String::from("hello");
    let s2 = s1; // Ownership of the string moves from s1 to s2!

    // println!("{}", s1); // Error! s1 is no longer valid because ownership was moved.
    println!("{}", s2);    // Works!
}

References and Borrowing
Instead of moving ownership (which makes variables unusable in the original scope), you can borrow values using references (& for immutable, &mut for mutable).
fn main() {
    let s1 = String::from("hello");

    let len = calculate_length(&s1); // Pass a reference to s1

    println!("The length of '{}' is {}.", s1, len); // s1 is still valid here!
}

fn calculate_length(s: &String) -> usize { // s is a reference to a String
    s.len()
}

Next Steps & Practice
To run these examples locally without installing anything yet, you can use the online Rust Playground (play.rust-lang.org).
Would you like to dive deeper into a specific concept next—such as Structs & Enums, Pattern Matching (match), or writing your first small project?
