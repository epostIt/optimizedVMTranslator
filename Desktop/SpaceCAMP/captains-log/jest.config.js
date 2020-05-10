module.exports = {
  roots: ['<rootDir>/src'],
  coveragePathIgnorePatterns: ['<rootDir>/src/__mocks__/*', '<rootDir>/src/assets/*'],
  moduleNameMapper: {
    '\\.(css|less)$': 'identity-obj-proxy'
  },
  resolver: null,
  transform: {
    '^.+\\.tsx?$': 'ts-jest',
    '^.+\\.svg$': '<rootDir>/svgTransform.js'
  }
};
