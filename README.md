Hello everyone
Now I will tell you the story of this project. Telegram bot written in Java + Spring Boot

It was an order from a blogger to process receipts and find a winner in the drawing.
The task of my telegram bots was that the user, having launched the bat, head to fall in one check for 7,900 tenge. 7900 is the price of one ticket. 
Accordingly, after processing the authenticity check, the bot sent a special UUID for the user 
(it could have been a random number, but for reasons of some confidentiality, I decided to issue a UUID) so that he can then find himself on the list of winners, if he is one.
I connected a message broker and a microservice on Node to it.js, which I decided to remove soon, as I considered it superfluous.

The Apache PDFBox libraries, Apache POI, and Tesseract technology were used.

Thanks for attention and reading! 
