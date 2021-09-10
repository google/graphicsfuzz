#version 450

/*
 * Copyright 2019 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

precision highp float;
precision highp int;

layout(location = 0) out vec4 _GLF_color;

uniform vec2 injectionSwitch;

/*
This shader populates data array with reversed values,
performs merge sort to get the data to the correct order,
and then draws an image based on the sorted values and
the screen coordinates.

The sorting operation uses purely integer math, and the
image drawing uses only linear operations (division,
addition) making it numerically stable.

Populating the input data uses an uniform named
injectionSwitch that is guaranteed to be populated with
the value (0.0, 1.0) to avoid the shader compiler from
optimizing the initialization.

The image drawing portion also contains discard operation
as one of the possible outputs.
*/


// Size of an array.
const int N = 10;
// An array and its temperary array whose elements will be sorted.
int data[10], temp[10];

// Merge two sorted subarrays data[from ... mid] and data[mid+1 ... to].
void merge(int from, int mid, int to)
{
 int k = from, i = from, j = mid + 1;
 while(i <= mid && j <= to)
  {
   if(data[i] < data[j])
    {
     temp[k ++] = data[i ++];
    }
   else
    {
     temp[k ++] = data[j ++];
    }
  }
 // Copy remaining elements.
 while(i < N && i <= mid)
  {
   temp[k ++] = data[i ++];
  }
 // Copy back to the original array.
 for(int i = from; i <= to; i ++)
  {
   data[i] = temp[i];
  }
}

// Sort array using the iterative approach.
void mergeSort()
{
 int low = 0;
 int high = N - 1;
 // Divide the array into blocks of size m.
 // m = [1, 2, 4 ,8, 16...].
 for(int m = 1; m <= high; m = 2 * m)
  {
   // For m = 1, i = [0, 2, 4, 6, 8].
   // For m = 2, i = [0, 4, 8].
   // For m = 4, i = [0, 8].
   for(int i = low; i < high; i += 2 * m)
    {
     int from = i;
     int mid = i + m - 1;
     int to = min(i + 2 * m - 1, high);
     // Merge the sub-array
     merge(from, mid, to);
     /*
     Status after each merge operation:
      3  4  2  1  0 -1 -2 -3 -4 -5
      3  4  1  2  0 -1 -2 -3 -4 -5
      3  4  1  2 -1  0 -2 -3 -4 -5
      3  4  1  2 -1  0 -3 -2 -4 -5
      3  4  1  2 -1  0 -3 -2 -5 -4
      1  2  3  4 -1  0 -3 -2 -5 -4
      1  2  3  4 -3 -2 -1  0 -5 -4
      1  2  3  4 -3 -2 -1  0 -5 -4
     -3 -2 -1  0  1  2  3  4 -5 -4
     -3 -2 -1  0  1  2  3  4 -5 -4
     -5 -4 -3 -2 -1  0  1  2  3  4
     */
    }
  }
}



void main()
{
 // Note the use of injectionSwitch uniform which is guaranteed
 // to be populated with values (0.0, 1.0) to avoid compiler
 // optimization of the population of initial data[] values.
 int i = int(injectionSwitch.x);
 do
  {
   switch(i)
    {
     case 0: data[i] =  4; break;
     case 1: data[i] =  3; break;
     case 2: data[i] =  2; break;
     case 3: data[i] =  1; break;
     case 4: data[i] =  0; break;
     case 5: data[i] = -1; break;
     case 6: data[i] = -2; break;
     case 7: data[i] = -3; break;
     case 8: data[i] = -4; break;
     case 9: data[i] = -5; break;
    }
   i++;
  }
 while(i < 10);

 for(int j = 0; j < 10; j ++)
  {
   temp[j] = data[j];
  }

 mergeSort();

 float grey;
 if(int(gl_FragCoord[1]) < 30)
  {
   grey = 0.5 + float(data[0]) / 10.0;
  }
 else
  {
   if(int(gl_FragCoord[1]) < 60)
    {
     grey = 0.5 + float(data[1]) / 10.0;
    }
   else
    {
     if(int(gl_FragCoord[1]) < 90)
      {
       grey = 0.5 + float(data[2]) / 10.0;
      }
     else
      {
       if(int(gl_FragCoord[1]) < 120)
        {
         grey = 0.5 + float(data[3]) / 10.0;
        }
       else
        {
         if(int(gl_FragCoord[1]) < 150)
          {
           discard;
          }
         else
          {
           if(int(gl_FragCoord[1]) < 180)
            {
             grey = 0.5 + float(data[5]) / 10.0;
            }
           else
            {
             if(int(gl_FragCoord[1]) < 210)
              {
               grey = 0.5 + float(data[6]) / 10.0;
              }
             else
              {
               if(int(gl_FragCoord[1]) < 240)
                {
                 grey = 0.5 + float(data[7]) / 10.0;
                }
               else
                {
                 if(int(gl_FragCoord[1]) < 270)
                  {
                   grey = 0.5 + float(data[8]) / 10.0;
                  }
                 else
                  {
                   discard;
                  }
                }
              }
            }
          }
        }
      }
    }
  }
 _GLF_color = vec4(vec3(grey), 1.0);
}

