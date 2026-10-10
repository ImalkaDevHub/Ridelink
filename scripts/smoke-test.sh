#!/bin/bash
set -e

ACCOUNT_URL="http://localhost:8081"
DRIVER_URL="http://localhost:8082"
RIDE_URL="http://localhost:8083"
FARE_URL="http://localhost:8084"

# Use random string for email to avoid unique constraint violations
RND=$RANDOM$RANDOM
PASS_EMAIL="pass_${RND}@example.com"
DRV_EMAIL="drv_${RND}@example.com"

echo "1. Register Passenger"
RES=$(curl -s -X POST "$ACCOUNT_URL/api/auth/register" -H "Content-Type: application/json" -d "{\"name\": \"Pass\", \"email\": \"$PASS_EMAIL\", \"password\": \"pass\", \"role\": \"PASSENGER\"}")
PASS_TOKEN=$(echo $RES | grep -o '"token":"[^"]*"' | head -n 1 | awk -F'"' '{print $4}')
if [ -z "$PASS_TOKEN" ]; then echo "Failed to get passenger token: $RES"; exit 1; fi

echo "2. Register Driver"
RES=$(curl -s -X POST "$ACCOUNT_URL/api/auth/register" -H "Content-Type: application/json" -d "{\"name\": \"Driv\", \"email\": \"$DRV_EMAIL\", \"password\": \"drv\", \"role\": \"DRIVER\"}")
DRV_TOKEN=$(echo $RES | grep -o '"token":"[^"]*"' | head -n 1 | awk -F'"' '{print $4}')
if [ -z "$DRV_TOKEN" ]; then echo "Failed to get driver token: $RES"; exit 1; fi

echo "3. Create Driver Profile"
RES=$(curl -s -X POST "$DRIVER_URL/api/drivers" -H "Content-Type: application/json" -H "Authorization: Bearer $DRV_TOKEN" -d "{\"name\": \"Driv\", \"email\": \"$DRV_EMAIL\", \"vehicleNumber\": \"DEF-$RND\", \"vehicleType\": \"Car\", \"serviceArea\": \"Colombo\"}")
DRV_ID=$(echo $RES | grep -o '"id":"[^"]*"' | head -n 1 | awk -F'"' '{print $4}')
if [ -z "$DRV_ID" ]; then echo "Failed to get driver id: $RES"; exit 1; fi

echo "4. Set Available"
RES=$(curl -s -X PATCH "$DRIVER_URL/api/drivers/$DRV_ID/availability?available=true" -H "Authorization: Bearer $DRV_TOKEN")
if ! echo $RES | grep -q '"available":true'; then echo "Failed to set availability: $RES"; exit 1; fi

echo "5. Estimate Fare"
RES=$(curl -s -X POST "$FARE_URL/api/fares/estimate" -H "Content-Type: application/json" -H "Authorization: Bearer $PASS_TOKEN" -d '{"pickupLocation": "Colombo", "destinationLocation": "Galle", "distanceKm": 120.0, "durationMin": 120, "vehicleType": "Car"}')
if ! echo $RES | grep -q '"estimatedFare"'; then echo "Failed to get estimate: $RES"; exit 1; fi

echo "6. Request Ride"
RES=$(curl -s -X POST "$RIDE_URL/api/rides" -H "Content-Type: application/json" -H "Authorization: Bearer $PASS_TOKEN" -d '{"passengerName": "Pass", "pickup": "Colombo", "destination": "Galle", "distance": 120.0, "duration": 120, "fare": 12000.0, "vehicleType": "Car"}')
RIDE_ID=$(echo $RES | grep -o '"id":"[^"]*"' | head -n 1 | awk -F'"' '{print $4}')
if [ -z "$RIDE_ID" ]; then echo "Failed to get ride id: $RES"; exit 1; fi

echo "7. Accept Ride"
RES=$(curl -s -X PATCH "$RIDE_URL/api/rides/$RIDE_ID/accept" -H "Authorization: Bearer $DRV_TOKEN")
if ! echo $RES | grep -q '"status":"ACCEPTED"'; then echo "Failed to accept ride: $RES"; exit 1; fi

echo "8. Start Ride"
RES=$(curl -s -X PATCH "$RIDE_URL/api/rides/$RIDE_ID/start" -H "Authorization: Bearer $DRV_TOKEN")
if ! echo $RES | grep -q '"status":"IN_PROGRESS"'; then echo "Failed to start ride: $RES"; exit 1; fi

echo "9. Complete Ride"
RES=$(curl -s -X PATCH "$RIDE_URL/api/rides/$RIDE_ID/complete" -H "Content-Type: application/json" -H "Authorization: Bearer $DRV_TOKEN" -d '{"distanceKm": 120.0, "durationMin": 120}')
if ! echo $RES | grep -q '"status":"COMPLETED"'; then echo "Failed to complete ride: $RES"; exit 1; fi

echo "10. Pay"
RES=$(curl -s -X POST "$FARE_URL/api/payments" -H "Content-Type: application/json" -H "Authorization: Bearer $PASS_TOKEN" -d "{\"rideId\": \"$RIDE_ID\", \"paymentMethod\": \"CARD\", \"cardLast4\": \"1234\"}")
PAY_ID=$(echo $RES | grep -o '"id":"[^"]*"' | head -n 1 | awk -F'"' '{print $4}')
if [ -z "$PAY_ID" ]; then echo "Failed to get payment id: $RES"; exit 1; fi

echo "11. Get Receipt"
RES=$(curl -s -X GET "$FARE_URL/api/payments/$PAY_ID/receipt" -H "Authorization: Bearer $PASS_TOKEN")
if ! echo $RES | grep -q '"receiptNumber"'; then echo "Failed to get receipt: $RES"; exit 1; fi

echo "Smoke test passed successfully!"
