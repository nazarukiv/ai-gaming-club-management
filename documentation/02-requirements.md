# 2. System Requirements

These requirements describe planned features. They are not implemented yet.

## 1. Functional Requirements

| ID | Requirement |
|----|-------------|
| FR-01 | Users can log in to their accounts. |
| FR-02 | Users can check available gaming PCs and PS5 stations. |
| FR-03 | Users can book one or more gaming stations. |
| FR-04 | Users can view and cancel their bookings. |
| FR-05 | System can calculate booking prices. |
| FR-06 | AI assistant can answer questions about prices and availability. |
| FR-07 | AI assistant can help users create bookings. |
| FR-08 | Users must confirm a booking before it is created. |
| FR-09 | System can find gaming PCs next to each other. |
| FR-10 | Authorised admins can view and manage bookings, and view stations and activity logs. |
| FR-11 | AI assistant can cancel bookings only after user confirmation. |

## 2. Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| NFR-01 | System must prevent double bookings. |
| NFR-02 | Users can only access their own bookings. Authorised admins can also access other users' bookings. |
| NFR-03 | Backend must validate all user inputs. |
| NFR-04 | Booking operations must be safe when multiple users book at the same time. |
| NFR-05 | AI cannot directly access or change the database. |
| NFR-06 | System must handle errors without crashing. |
| NFR-07 | System must handle UK time correctly. |
| NFR-08 | Booking changes must be saved in an activity log. |

## 3. Business Rules

- The club has 30 gaming PCs and 5 PS5 stations.
- Users cannot book stations that are already booked for the same time.
- Bookings must be within club opening hours.
- Booking price depends on station type and duration.
- Users must confirm bookings before they are created.
- AI cannot create or cancel bookings without user confirmation.
- Only the booking owner or an authorised admin can cancel a booking.
- All booking times use the Europe/London timezone.
