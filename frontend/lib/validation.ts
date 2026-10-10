// Client-side copies of the backend's validation rules (account-service
// RegisterRequest / PasswordChangeRequest), so most mistakes are caught
// before a round trip. The backend still validates everything.

export const PASSWORD_HINT = "8+ characters, with a letter and a number";

export function validateName(name: string): string | null {
  const trimmed = name.trim();
  if (!trimmed) return "Name is required.";
  if (trimmed.length < 2 || trimmed.length > 60) return "Name must be 2-60 characters.";
  return null;
}

export function validateEmail(email: string): string | null {
  const trimmed = email.trim();
  if (!trimmed) return "Email is required.";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) return "Enter a valid email address.";
  return null;
}

export function validatePassword(password: string): string | null {
  if (!password) return "Password is required.";
  if (password.length < 8 || password.length > 72) return "Password must be 8-72 characters.";
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return "Password must contain at least one letter and one number.";
  return null;
}
