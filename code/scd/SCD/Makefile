HDR = Losses.h cmd_line.h
SRC = cmd_line.cc

CC = g++

CFLAGS = -O3
LFLAGS = -lm

OBJS = $(SRC:.cc=.o)

all: scd

scd: $(OBJS) scd.o
	$(CC) $(OBJS) scd.o $(LFLAGS) -o scd

cyclic: $(OBJS) cyclic.o1
	$(CC) $(OBJS) cyclic.o1 $(LFLAGS) -o cyclic

cyclic.o1: scd.cc
	$(CC) $(CFLAGS) -c -D _CYCLIC_ scd.cc -o cyclic.o1

greedy: $(OBJS) greedy.o1
	$(CC) $(OBJS) greedy.o1 $(LFLAGS) -o greedy

greedy.o1: scd.cc
	$(CC) $(CFLAGS) -c -D _GREEDY_ scd.cc -o greedy.o1

%.o: %.c $(HDR)
	$(CC) $(CFLAGS) -c $<

clean:
	rm -f *.o *.o1 *~ scd greedy cyclic
