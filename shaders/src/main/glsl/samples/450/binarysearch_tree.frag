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

layout(location = 0) out vec4 _GLF_color;

uniform vec2 resolution;

struct BST{
    int data;
    int leftIndex;
    int rightIndex;
};

BST tree[10];

void makeTreeNode(inout BST tree, int data)
{
    tree.data = data;
    tree.leftIndex = -1;
    tree.rightIndex = -1;
}

void insert(int treeIndex, int data)
{
    int baseIndex = 0;
    while (baseIndex <= treeIndex) {
        // If new value is smaller thatn the current node, we known that we will have
        // add this element in the left side.
        if (data <= tree[baseIndex].data) {
          // If a left subtree of the current node is empty, the new node is added as
          // a left subtree of the current node.
          if (tree[baseIndex].leftIndex == -1) {
              tree[baseIndex].leftIndex = treeIndex;
              makeTreeNode(tree[treeIndex], data);
              return;
          } else {
              baseIndex = tree[baseIndex].leftIndex;
              continue;
          }
        } else {
            // If a right subtree of the current node is empty, the new node is added as
            // a right subtree of the current node.
            if (tree[baseIndex].rightIndex == -1) {
                tree[baseIndex].rightIndex = treeIndex;
                makeTreeNode(tree[treeIndex], data);
                return;
            } else {
                baseIndex = tree[baseIndex].rightIndex;
                continue;
            }
        }
    }
}

// Return element data if the given target exists in a tree. Otherwise, we simply return -1.
int search(int target){
    BST currentNode;
    int index = 0;
    while (index != -1) {
        currentNode = tree[index];
        if (currentNode.data == target) {
            return target;
        }
        index = target > currentNode.data ? currentNode.rightIndex : currentNode.leftIndex;
    }
    return -1;
}

vec3 hueColor(float angle) {
    float nodeData = float(search(15));
    vec3 color;
    color = clamp(fract(angle * vec3(1.0, 5.0, nodeData)), 0.0, 1.0);
    color.x *= cosh(isnan(float(search(30))) ? 0.0 : 1.0);
    return color;
}

float makeFrame(float v) {
    v *= 6.5;
    if (v < 1.5) {
        return float(search(100));
    }
    if (v < 4.0) {
        return 0.0;
    }
    if (v < float(search(6))) {
        return  1.0;
    }
    return  10.0 + float(search(30));
}

/*
* This shader implements binary search tree using an array data structure. The elements of
* tree are kept in the array that contains a list of BST object holding indices of left and
* right subtree in the array.
*
* - Tree representation of the number used in this shader:
*            9
*         /    \
*        5      12
*      /  \      \
*     2   7      15
*        / \    /  \
*       6  8   13  17
*
* - Array representation:
* [9, 5, 12, 15, 7, 8, 2, 6, 17, 13]
*
*/

void main() {
    int treeIndex = 0;
    // Initialize root node.
    makeTreeNode(tree[0], 9);
    // Each time we insert a new node into the tree, we increment one.
    treeIndex++;

    insert(treeIndex, 5);
    treeIndex++;
    insert(treeIndex, 12);
    treeIndex++;
    insert(treeIndex, 15);
    treeIndex++;
    insert(treeIndex, 7);
    treeIndex++;
    insert(treeIndex, 8);
    treeIndex++;
    insert(treeIndex, 2);
    treeIndex++;
    insert(treeIndex, 6);
    treeIndex++;
    insert(treeIndex, 17);
    treeIndex++;
    insert(treeIndex, 13);

    vec2 z = (gl_FragCoord.yx / resolution);
    float x = makeFrame(z.x);
    float y = makeFrame(z.y);

    int sum = -100;
    for (int target = 0; target < 20; target ++) {
        int result = search(target);
        if (result > 0) {
            sum += result;
        } else {
            switch (result) {
                case -1:
                    sum += 1;
                break;
                case 0:
                    return;
          }
        }
    }
    float a = tan(x + y * float(sum));
    _GLF_color = vec4(hueColor(a), 1.);

}
