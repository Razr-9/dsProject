int numcount(int number){
	while (number>9){
			int num1000, num100, num10,num1;

			num1000 = number / 1000;
			num100 = (number % 1000) /100;
			num10 = (number % 100) / 10;
			num1 = number % 10;
			
			number = num1000 + num100 + num10 + num1;
		}
	return (number);
}

