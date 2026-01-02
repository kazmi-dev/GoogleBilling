# 🧾 GoogleBilling — With Hilt + Without Hilt

A reusable, clean, and modern implementation of **Google Play Billing** using Kotlin, **Dagger Hilt**, and **coroutines**.  
Supports **one-time purchases** (`INAPP`) and **subscriptions** (`SUBS`) with proper handling for purchase states, errors, and acknowledgment.

---

## 📦 Features

- ✅ Two-way billing package -> DI & Object
- ✅ Supports **in-app purchases** and **subscriptions**
- ✅ Handles **acknowledgment**, **pending**, and **already purchased** states
- ✅ Uses `BillingClient` with proper lifecycle handling
- ✅ Coroutine-powered product querying
- ✅ Plug-and-play **State Flows** (`products, billingEvents`)
- ✅ Built with **Dagger Hilt** for dependency injection
- ✅ Support MVVM Architecture (viewModel & repository)
- ✅ Support billing through **Object**

---

## 📚 Prerequisites

- ✅ Billing Library
- ✅ Dependency Injection (Dagger/Hilt)
- ✅ NO Dependency Injection (Dagger/Hilt) with Util as an Object
